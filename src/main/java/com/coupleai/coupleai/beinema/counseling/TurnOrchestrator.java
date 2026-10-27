package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.DTO.counseling.QuestionResponse;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.DecisionStatus;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.DirectorDecisionRecordRepository;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import com.coupleai.coupleai.beinema.Service.MessageServiceImpl;
import com.coupleai.coupleai.beinema.governance.GovernanceAction;
import com.coupleai.coupleai.beinema.governance.GovernanceDecision;
import com.coupleai.coupleai.beinema.governance.GovernanceTarget;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import com.coupleai.coupleai.beinema.governance.QuestionMarkers;
import com.coupleai.coupleai.beinema.governance.ReplySegments;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs one counselor turn of the v3 pipeline:
 * <ol>
 *   <li>load the material (memory, intents, questions, messages, safety screen, rule cards);</li>
 *   <li>the director decides (JSON); one repair if the JSON is broken; one safety review
 *       if a strong safety cue was ignored; the safe fallback if it still fails;</li>
 *   <li>the platform validates and stores the decision and the memory in one transaction;</li>
 *   <li>governance is applied; a silent move only sends its small signal;</li>
 *   <li>the voice writes the words; a structured question is opened and delivered last.</li>
 * </ol>
 * The caller holds the room lock. Nothing the partners wrote is lost when a step fails.
 */
@Service
@RequiredArgsConstructor
public class TurnOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TurnOrchestrator.class);

    private final TurnMaterialLoader loader;
    private final BrainGateway brain;
    private final CounselingConfig config;
    private final TurnPersistence persistence;
    private final QuestionService questionService;
    private final ChatRealtimeService chatRealtimeService;
    private final ChatRoomEventHub eventHub;
    private final ParticipationPauseService participationPauseService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final DirectorDecisionRecordRepository decisionRepository;
    private final TurnInbox inbox;

    /** The lines shown while the counselor prepares its reply; optional (absent in the tests that build this by hand). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StatusLineWriter statusLines;

    /** Typing pauses between the parts of a turn; off in tests. */
    boolean pacing = true;

    /** Something to deliver: a part of the counselor's words, or the structured question. */
    public sealed interface Item permits TextItem, QuestionItem {
    }

    public record TextItem(ReplySegments.Segment segment, boolean replyToTrigger) implements Item {
    }

    public record QuestionItem(Long questionId, String publicId, String text, List<QuestionOptions.Option> options)
            implements Item {
    }

    /** remaining: what is delivered after the sender is released, with typing pauses. */
    public record Outcome(Long roomId, Long agentId, Long triggerId, List<Item> remaining, boolean delivered,
                          boolean failed, String error) {

        static Outcome nothing(Long roomId) {
            return new Outcome(roomId, null, null, List.of(), false, false, null);
        }
    }

    /**
     * One turn about the partners' new messages, or about a moment of the session
     * (it opened, a partner entered it) when nobody wrote: then there is no
     * trigger message and the counselor always speaks.
     */
    public Outcome runTurn(Long roomId) {
        expireStaleQuestions(roomId);
        TurnMaterial m = loader.load(roomId);
        if (!m.hasNewMessages() && !m.hasMoment()) {
            return Outcome.nothing(roomId);
        }
        if (m.room() != null && m.room().getStatus() == com.coupleai.coupleai.beinema.Enum.ChatRoomStatus.ENDED) {
            // the owner ended the conversation for good: the counselor does not speak any more
            return Outcome.nothing(roomId);
        }
        Long trigger = m.triggerMessageId();
        if (trigger != null) {
            DirectorDecisionRecord existing = decisionRepository.findByChatRoomIdAndTriggerMessageId(roomId, trigger).orElse(null);
            if (existing != null && (existing.getStatus() == DecisionStatus.DONE || existing.getStatus() == DecisionStatus.FALLBACK)
                    && existing.getVoiceOutput() != null) {
                // already answered (a repeated request); never answer twice
                return Outcome.nothing(roomId);
            }
        }
        Long agentId = agentId(roomId);
        chatRealtimeService.publishAgentTyping(roomId, 9000, "thinking");
        if (statusLines != null && trigger != null) {
            // next to the real turn: two short lines about what was just written
            statusLines.startFor(roomId, m.fresh().stream()
                    .map(Message::getContent)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n")));
        }

        // 0. the session manager plans the session at its start and again every so often
        SessionPlanner.refresh(m, brain);

        // 1-2. decide
        String packet = DirectorPacketBuilder.build(m, null);
        Decided decided = decide(m, packet);
        DirectorDecision d = decided.decision();
        if (trigger == null && d.silent()) {
            // a moment of the session is never met with silence
            d.notes.add("moment: a silent decision became RESPOND");
            d.action = "RESPOND";
            d.route = "SPEAK";
            d.react = null;
            d.sticker = null;
            if (d.briefDo.isBlank()) {
                d.briefDo = "Welcome them briefly and say where the work stands.";
            }
        }

        // 3. store decision and memory
        TurnPersistence.Stored stored = persistence.store(m, d, decided.status(), packet.length(), decided.raw(),
                decided.millis(), decided.error());
        publishQuestionChanges(m, stored.changedQuestions());

        // 4. governance and silence
        applyGovernance(roomId, d);
        if (d.silent()) {
            if (d.react != null) {
                chatRealtimeService.reactAndPublish(roomId, trigger, d.react);
            } else if (d.sticker != null) {
                chatRealtimeService.saveStickerAndPublish(roomId, agentId, d.sticker, trigger);
            }
            persistence.finish(stored.decisionId(), "", 0, List.of());
            inbox.processed(roomId, trigger, m.notes());
            return new Outcome(roomId, agentId, trigger, List.of(), true, false, null);
        }

        // 5. a lesson: the teacher fits it to the couple, then the voice says it
        LessonTeacher.Result taught = LessonTeacher.prepare(m, d, brain);

        // 6. the voice (still writing: keep the typing sign up for both partners)
        chatRealtimeService.publishAgentTyping(roomId, 9000, "writing");
        String voicePacket = VoicePacketBuilder.forDecision(m, d, stored.readings(), config.getVoiceRecentMessages());
        String voiceRaw = "";
        long voiceMs = 0;
        VoiceOutputParser.Result out = null;
        for (int attempt = 0; attempt < 2 && (out == null || out.failed()); attempt++) {
            try {
                BrainGateway.Reply reply = brain.voice(voicePacket);
                voiceRaw = reply.text();
                voiceMs += reply.millis();
                out = VoiceOutputParser.parse(voiceRaw);
                if (!out.failed() && out.text().isBlank() && (out.questionText() == null || out.questionText().isBlank())) {
                    out = new VoiceOutputParser.Result("", null, Map.of(), false, "EMPTY", out.notes());
                }
            } catch (RuntimeException exception) {
                log.warn("Voice call failed in room {}: {}", roomId, exception.getMessage());
                out = new VoiceOutputParser.Result("", null, Map.of(), false, "CALL_FAILED: " + exception.getMessage(), List.of());
            }
        }
        // the reply must not hand their words back to them; if it does, the voice writes it once more
        String echoNote = null;
        if (out != null && !out.failed() && trigger != null) {
            List<String> theirs = m.fresh().stream()
                    .map(Message::getContent)
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
            java.util.Optional<String> echo = EchoGuard.echoed(out.text(), theirs);
            if (echo.isPresent()) {
                String warning = "your first draft repeated their words «" + echo.get() + "»: never repeat, paraphrase or "
                        + "quote what they wrote or asked; go straight to the move; ";
                String again = voicePacket.contains("must_not=")
                        ? voicePacket.replaceFirst("must_not=", java.util.regex.Matcher.quoteReplacement("must_not=" + warning))
                        : voicePacket.replaceFirst("length=", java.util.regex.Matcher.quoteReplacement("must_not=" + warning + "\nlength="));
                try {
                    BrainGateway.Reply reply = brain.voice(again);
                    voiceMs += reply.millis();
                    VoiceOutputParser.Result rewritten = VoiceOutputParser.parse(reply.text());
                    boolean usable = !rewritten.failed()
                            && (!rewritten.text().isBlank() || (rewritten.questionText() != null && !rewritten.questionText().isBlank()));
                    if (usable) {
                        out = rewritten;
                        voiceRaw = reply.text();
                        echoNote = "echo: rewritten (it repeated «" + echo.get() + "»)";
                    }
                } catch (RuntimeException exception) {
                    log.warn("Voice rewrite failed in room {}: {}", roomId, exception.getMessage());
                }
            }
        }
        if (out == null || out.failed()) {
            if (stored.questionId() != null) {
                questionService.cancel(stored.questionId(), "the voice could not write it");
            }
            String error = out == null ? "VOICE_FAILED" : out.error();
            persistence.failVoice(roomId, stored.decisionId(), stored.previousProcessedMessageId(), voiceRaw, voiceMs, error);
            if (trigger == null) {
                // a moment is not tried again on every later turn
                inbox.processed(roomId, null, m.notes());
            }
            return new Outcome(roomId, agentId, trigger, List.of(), false, true, error);
        }

        // 7. deliver
        List<String> notes = new ArrayList<>(out.notes());
        if (echoNote != null) {
            notes.add(echoNote);
        }
        if (taught != null) {
            notes.add(taught.note() + " (" + taught.millis() + " ms)");
        }
        Long addresseeId = PartnerRole.isPartner(d.addressee) ? m.userIdOf(d.addressee) : null;
        if (trigger != null && addresseeId != null && loader.personWroteAfter(roomId, addresseeId, trigger)) {
            // the person this reply speaks to wrote again meanwhile: the next turn answers them with everything
            if (stored.questionId() != null) {
                questionService.cancel(stored.questionId(), "the addressee wrote again before it was shown");
            }
            persistence.finish(stored.decisionId(), voiceRaw, voiceMs, List.of("not delivered: the addressee wrote again"));
            inbox.processed(roomId, trigger, m.notes());
            return new Outcome(roomId, agentId, trigger, List.of(), false, false, null);
        }
        List<Item> items = items(m, d, stored, out, notes);
        if (trigger == null && m.notes().stream().anyMatch(note -> note.messageId() == null
                && note.text().contains("PARTNER_CONSENTED"))) {
            // the thank-you after consent is one paragraph, never a run of short messages
            items = oneParagraph(items);
        }
        persistence.finish(stored.decisionId(), voiceRaw, voiceMs, notes);
        inbox.processed(roomId, trigger, m.notes());
        if (items.isEmpty()) {
            return new Outcome(roomId, agentId, trigger, List.of(), false, false, null);
        }
        if (trigger != null && items.get(0) instanceof TextItem first && !first.replyToTrigger()
                && loader.someoneWroteAfter(roomId, trigger)) {
            // a partner wrote while the counselor was thinking: show what this reply answers
            items.set(0, new TextItem(first.segment(), true));
        }
        deliver(roomId, agentId, trigger, items.get(0));
        return new Outcome(roomId, agentId, trigger, new ArrayList<>(items.subList(1, items.size())), true, false, null);
    }

    /** The rest of the counselor's turn, after the sender has been released. */
    public void deliverRemaining(Outcome outcome) {
        if (outcome == null) {
            return;
        }
        for (Item item : outcome.remaining()) {
            long typing = item instanceof TextItem text
                    ? MessageServiceImpl.pacingMillis(text.segment())
                    : Math.min(5200L, 1200L + ((QuestionItem) item).text().length() * 30L);
            chatRealtimeService.publishAgentTyping(outcome.roomId(), typing, "continuing");
            if (pacing) {
                sleep(typing);
            }
            deliver(outcome.roomId(), outcome.agentId(), outcome.triggerId(), item);
        }
    }

    // ------------------------------------------------------------------ steps

    private record Decided(DirectorDecision decision, DecisionStatus status, String raw, long millis, String error) {
    }

    private Decided decide(TurnMaterial m, String packet) {
        DecisionValidator.Context ctx = context(m);
        StringBuilder raw = new StringBuilder();
        long millis = 0;
        try {
            BrainGateway.Reply reply = brain.director(packet);
            raw.append(reply.text());
            millis += reply.millis();
            DirectorOutputParser.Result parsed = DirectorOutputParser.parse(reply.text());
            if (!parsed.ok() && !"DIRECTOR_FAILED".equals(parsed.error())) {
                BrainGateway.Reply repair = brain.director(DirectorPacketBuilder.build(m,
                        "REPAIR: " + parsed.error() + ". Return the same decision as one valid JSON object. Your previous output was:\n"
                                + clip(reply.text(), 6000)));
                raw.append("\n----- repair -----\n").append(repair.text());
                millis += repair.millis();
                parsed = DirectorOutputParser.parse(repair.text());
            }
            if (!parsed.ok()) {
                return new Decided(DecisionValidator.fallback(m.defaultAddressee(), parsed.error()), DecisionStatus.FALLBACK,
                        raw.toString(), millis, parsed.error());
            }
            DirectorDecision decision = DecisionValidator.validate(parsed.root(), ctx);
            if (m.screen().high() && DecisionValidator.rank(decision.safetyLevel) == 0) {
                String cues = m.screen().cues().stream()
                        .map(cue -> cue.category() + " \"" + cue.phrase() + "\" in #" + cue.messageId())
                        .collect(Collectors.joining("; "));
                BrainGateway.Reply review = brain.director(DirectorPacketBuilder.build(m,
                        "SAFETY_REVIEW: the screen found " + cues + " and your decision set safety level NONE. "
                                + "Look at those messages again. If it is a real signal, decide with the safety rules; "
                                + "if it is not, keep your decision and say why in safety.note."));
                raw.append("\n----- safety review -----\n").append(review.text());
                millis += review.millis();
                DirectorOutputParser.Result again = DirectorOutputParser.parse(review.text());
                if (again.ok()) {
                    DirectorDecision reviewed = DecisionValidator.validate(again.root(), ctx);
                    if (DecisionValidator.rank(reviewed.safetyLevel) > 0) {
                        reviewed.notes.add("safety review raised the level to " + reviewed.safetyLevel);
                        decision = reviewed;
                    } else {
                        decision.notes.add("safety_mismatch: strong cue kept at NONE after review: " + reviewed.safetyNote);
                    }
                } else {
                    decision.notes.add("safety_mismatch: the safety review could not be read");
                }
            }
            return new Decided(decision, DecisionStatus.DONE, raw.toString(), millis, null);
        } catch (RuntimeException exception) {
            log.warn("Director call failed in room {}: {}", m.roomId(), exception.getMessage());
            return new Decided(DecisionValidator.fallback(m.defaultAddressee(), "CALL_FAILED"), DecisionStatus.FALLBACK,
                    raw.toString(), millis, "CALL_FAILED: " + exception.getMessage());
        }
    }

    DecisionValidator.Context context(TurnMaterial m) {
        Map<Long, DecisionValidator.IntentView> intents = new LinkedHashMap<>();
        for (ClientIntent intent : m.intents()) {
            intents.put(intent.getId(), new DecisionValidator.IntentView(intent.getId(), intent.getParticipantRole(),
                    intent.getStatus().name()));
        }
        Map<Long, DecisionValidator.QuestionView> open = new LinkedHashMap<>();
        for (ClarifyingQuestion question : m.openQuestions()) {
            open.put(question.getId(), new DecisionValidator.QuestionView(question.getId(), question.getParticipantRole()));
        }
        return new DecisionValidator.Context(
                m.partnerRoles(),
                m.defaultAddressee(),
                m.looseTextsByRole(),
                m.roleByMessageId(),
                intents,
                open,
                m.snapshot().plans.stream().map(plan -> plan.id).collect(Collectors.toSet()),
                (int) m.questionsThisSession(),
                config.getQuestionsPerSession(),
                m.screen().high(),
                m.snapshot().safety.level,
                m.reachedStages()
        );
    }

    private List<Item> items(TurnMaterial m, DirectorDecision d, TurnPersistence.Stored stored, VoiceOutputParser.Result out,
                             List<String> notes) {
        List<Item> items = new ArrayList<>();
        if (!out.text().isBlank()) {
            ReplySegments.Parsed parsed = ReplySegments.parse(out.text());
            for (int index = 0; index < parsed.segments().size(); index++) {
                items.add(new TextItem(parsed.segments().get(index), index == 0 && parsed.replyToTrigger()));
            }
        }
        if (stored.questionId() == null) {
            if (out.questionBlock() && out.questionText() != null && !out.questionText().isBlank()) {
                notes.add("voice wrote a question block without a structured question; sent as text");
                items.add(new TextItem(new ReplySegments.Segment(out.questionText(), 0), false));
            }
            return items;
        }
        // the structured question
        List<QuestionOptions.Option> options = null;
        String refusal = null;
        if (!out.questionBlock() || out.questionText() == null || out.questionText().isBlank()) {
            refusal = "the voice did not write the question block";
        } else if ("CONFIRM".equals(d.question.type)) {
            options = QuestionOptions.forConfirm(stored.readings().keySet().iterator().next());
        } else {
            Map<Long, String> labels = new LinkedHashMap<>();
            for (Long intentId : stored.readings().keySet()) {
                String label = out.options().get("i" + intentId);
                if (label != null && !label.isBlank()) {
                    labels.put(intentId, PersianText.clip(label, 120));
                }
            }
            if (labels.size() < 2) {
                refusal = "the voice wrote " + labels.size() + " usable options";
            } else {
                options = QuestionOptions.forChoose(labels);
            }
        }
        if (refusal != null) {
            notes.add("question cancelled: " + refusal);
            questionService.cancel(stored.questionId(), refusal);
            if (out.questionText() != null && !out.questionText().isBlank()) {
                items.add(new TextItem(new ReplySegments.Segment(out.questionText(), 0), false));
            }
            return items;
        }
        items.add(new QuestionItem(stored.questionId(), stored.questionPublicId(), out.questionText(), options));
        return items;
    }

    /** The text parts joined into one message; stickers dropped, a question card kept after it. */
    static List<Item> oneParagraph(List<Item> items) {
        List<String> texts = new ArrayList<>();
        List<Item> rest = new ArrayList<>();
        for (Item item : items) {
            if (item instanceof TextItem text) {
                if (!text.segment().isSticker() && text.segment().text() != null && !text.segment().text().isBlank()) {
                    texts.add(text.segment().text().trim());
                }
            } else {
                rest.add(item);
            }
        }
        List<Item> joined = new ArrayList<>();
        if (!texts.isEmpty()) {
            joined.add(new TextItem(new ReplySegments.Segment(String.join(" ", texts), 0), false));
        }
        joined.addAll(rest);
        return joined;
    }

    private void deliver(Long roomId, Long agentId, Long triggerId, Item item) {
        if (item instanceof TextItem text) {
            ReplySegments.Segment segment = text.segment();
            if (segment.isSticker()) {
                chatRealtimeService.saveStickerAndPublish(roomId, agentId, segment.stickerToken(), triggerId);
            } else {
                chatRealtimeService.saveAgentAndPublish(roomId, agentId, segment.text(), text.replyToTrigger() ? triggerId : null);
            }
            return;
        }
        QuestionItem question = (QuestionItem) item;
        List<ClarifyingQuestion> expired = questionService.open(question.questionId(), question.text(), question.options());
        Message message = chatRealtimeService.saveAgentAndPublish(roomId, agentId,
                QuestionMarkers.questionContent(question.publicId(), question.text()));
        if (message != null) {
            questionService.attachMessage(question.questionId(), message.getId());
        }
        for (ClarifyingQuestion old : expired) {
            eventHub.publishNamed(roomId, "question_updated", QuestionViews.view(old, null));
        }
    }

    private void publishQuestionChanges(TurnMaterial m, List<ClarifyingQuestion> changed) {
        for (ClarifyingQuestion question : changed) {
            QuestionResponse view = QuestionViews.view(question, m.nameOf(question.getParticipantRole()));
            if (view != null) {
                eventHub.publishNamed(m.roomId(), "question_updated", view);
            }
        }
    }

    private void expireStaleQuestions(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return;
        }
        int session = room.getSessionNumber() == null ? 1 : room.getSessionNumber();
        for (ClarifyingQuestion question : questionService.expireStale(roomId, session)) {
            QuestionResponse view = QuestionViews.view(question, null);
            if (view != null) {
                eventHub.publishNamed(roomId, "question_updated", view);
            }
        }
    }

    private void applyGovernance(Long roomId, DirectorDecision d) {
        if ("NONE".equals(d.gov)) {
            return;
        }
        try {
            ChatRoom live = chatRoomRepository.findById(roomId).orElse(null);
            if (live != null) {
                participationPauseService.apply(live, new GovernanceDecision(
                        null,
                        GovernanceAction.fromToken(d.gov),
                        GovernanceTarget.fromToken(d.govTarget),
                        d.govDuration,
                        clip(d.rationale, 300),
                        "",
                        false
                ));
            }
        } catch (RuntimeException exception) {
            log.warn("Governance could not be applied in room {}: {}", roomId, exception.getMessage());
        }
    }

    private Long agentId(Long roomId) {
        return chatRoomAgentRepository.findActiveAgentIds(roomId).stream().findFirst().orElse(0L);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
