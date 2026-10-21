package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.CounselingState;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Enum.DecisionKind;
import com.coupleai.coupleai.beinema.Enum.DecisionStatus;
import com.coupleai.coupleai.beinema.Repository.ClientIntentRepository;
import com.coupleai.coupleai.beinema.Repository.CounselingStateRepository;
import com.coupleai.coupleai.beinema.Repository.DirectorDecisionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores one decided turn in a single transaction: the decision record, the
 * intent operations, the question draft, the question updates, the effect of
 * the previous move and the new memory. Either all of it is stored or none.
 * A fallback turn only marks the messages as handled and leaves memory as it was.
 */
@Service
@RequiredArgsConstructor
public class TurnPersistence {

    private final DirectorDecisionRecordRepository decisionRepository;
    private final CounselingStateRepository stateRepository;
    private final CounselingStateStore stateStore;
    private final ClientIntentRepository intentRepository;
    private final IntentLedger ledger;
    private final QuestionService questionService;
    private final com.coupleai.coupleai.beinema.Repository.PersonMilestoneRepository milestoneRepository;

    /** What the rest of the turn needs: ids, and the readings of a question in option order. */
    public record Stored(Long decisionId, Long questionId, String questionPublicId, Map<Long, String> readings,
                         List<ClarifyingQuestion> changedQuestions, Long previousProcessedMessageId) {
    }

    @Transactional
    public Stored store(TurnMaterial m, DirectorDecision d, DecisionStatus status, int packetChars, String raw,
                        long directorMs, String error) {
        Long trigger = m.triggerMessageId();
        // a moment of the session has no trigger message: always a record of its own
        DirectorDecisionRecord record = trigger == null
                ? new DirectorDecisionRecord()
                : decisionRepository.findByChatRoomIdAndTriggerMessageId(m.roomId(), trigger)
                .orElseGet(DirectorDecisionRecord::new);
        record.setChatRoomId(m.roomId());
        record.setTriggerMessageId(trigger);
        record.setNewMessageIds(m.fresh().stream().map(message -> String.valueOf(message.getId())).collect(Collectors.joining(",")));
        record.setKind(DecisionKind.TURN);
        record.setStatus(status);
        record.setAction(d.action);
        record.setAddressee(d.addressee);
        record.setSessionNumber(m.sessionNumber());
        record.setPacketChars(packetChars);
        record.setRawOutput(clip(raw, 60000));
        record.setDirectorMs(directorMs);
        record.setError(clip(error, 1000));
        record.setRetrievedCards(clip(m.cards().stream().map(KbCard::id).collect(Collectors.joining(",")), 500));
        record.setRulesUsed(clip(String.join(",", d.rulesUsed), 500));
        record.setSafetyLevel(d.safetyLevel);
        record.setValidationNotes(String.join("\n", d.notes));
        decisionRepository.save(record);
        Long decisionId = record.getId();

        CounselingState state = m.state();
        Long previousProcessed = state.getLastProcessedMessageId();
        CounselingSnapshot snapshot = m.snapshot();
        Long questionId = null;
        String questionPublicId = null;
        Map<Long, String> readings = new LinkedHashMap<>();
        List<ClarifyingQuestion> changed = new ArrayList<>();

        if (status == DecisionStatus.DONE) {
            Map<String, Long> userIdByRole = new HashMap<>();
            m.partners().forEach(partner -> userIdByRole.put(partner.role(), partner.userId()));
            List<DirectorDecision.IntentOp> fresh = withoutDuplicates(d, m.intents());
            Map<String, Long> refs = ledger.apply(m.roomId(), userIdByRole, fresh, decisionId, m.sessionNumber());

            if (d.question != null) {
                Map<Long, String> meanings = new HashMap<>();
                m.intents().forEach(intent -> meanings.put(intent.getId(), intent.getInterpretation()));
                for (DirectorDecision.IntentOp op : fresh) {
                    if ("CREATE".equals(op.op) && refs.containsKey(op.ref)) {
                        meanings.put(refs.get(op.ref), op.interpretation);
                    }
                }
                List<Long> ids = new ArrayList<>();
                for (String reading : d.question.intents) {
                    Long id = refs.containsKey(reading) ? refs.get(reading) : DecisionValidator.id(reading);
                    if (id != null && !ids.contains(id)) {
                        ids.add(id);
                        readings.put(id, meanings.getOrDefault(id, ""));
                    }
                }
                boolean enough = "CONFIRM".equals(d.question.type) ? ids.size() == 1 : ids.size() >= 2;
                Long addressee = m.userIdOf(d.question.addressee);
                if (enough && addressee != null) {
                    ClarifyingQuestion draft = questionService.createDraft(m.roomId(), decisionId, d.question, addressee, ids,
                            m.sessionNumber());
                    questionId = draft.getId();
                    questionPublicId = draft.getPublicId();
                } else {
                    d.question = null;
                    readings.clear();
                    d.notes.add("question dropped: its readings could not be stored");
                }
            }
            changed.addAll(questionService.applyUpdates(m.roomId(), d.questionUpdates, decisionId));

            if (!m.lastTurns().isEmpty()) {
                DirectorDecisionRecord previous = m.lastTurns().get(0);
                if (!previous.getId().equals(decisionId) && previous.getOutcomeEffect() == null) {
                    decisionRepository.findById(previous.getId()).ifPresent(prior -> {
                        prior.setOutcomeEffect(d.previousEffect);
                        prior.setOutcomeNote(clip(d.previousNote, 500));
                        decisionRepository.save(prior);
                    });
                }
            }
            SnapshotUpdater.apply(snapshot, d, decisionId, m.sessionNumber());

            // stages of each partner's own path reached in this turn
            for (DirectorDecision.MilestoneOp op : d.milestones) {
                Long userId = userIdByRole.get(op.participant);
                if (userId == null) {
                    continue;
                }
                com.coupleai.coupleai.beinema.Entity.PersonMilestone milestone =
                        new com.coupleai.coupleai.beinema.Entity.PersonMilestone();
                milestone.setChatRoomId(m.roomId());
                milestone.setUserId(userId);
                milestone.setParticipantRole(op.participant);
                milestone.setStage(op.stage);
                milestone.setSessionNumber(m.sessionNumber());
                milestone.setNote(op.note);
                milestone.setDecisionId(decisionId);
                milestoneRepository.save(milestone);
            }
        }

        if (trigger != null) {
            state.setLastProcessedMessageId(trigger);
        }
        state.setSessionNumber(m.sessionNumber());
        stateStore.save(state, snapshot);

        record.setAcceptedJson(CounselingStateStore.json(d));
        record.setValidationNotes(String.join("\n", d.notes));
        decisionRepository.save(record);
        return new Stored(decisionId, questionId, questionPublicId, readings, changed, previousProcessed);
    }

    /** The voice could not speak: the turn failed and its messages wait for the next turn. */
    @Transactional
    public void failVoice(Long roomId, Long decisionId, Long previousProcessed, String voiceRaw, long voiceMs, String error) {
        decisionRepository.findById(decisionId).ifPresent(record -> {
            record.setStatus(DecisionStatus.FAILED);
            record.setVoiceOutput(clip(voiceRaw, 20000));
            record.setVoiceMs(voiceMs);
            record.setError(clip(error, 1000));
            decisionRepository.save(record);
        });
        stateRepository.findByChatRoomId(roomId).ifPresent(state -> {
            state.setLastProcessedMessageId(previousProcessed);
            stateRepository.save(state);
        });
    }

    @Transactional
    public void finish(Long decisionId, String voiceRaw, long voiceMs, List<String> notes) {
        decisionRepository.findById(decisionId).ifPresent(record -> {
            record.setVoiceOutput(clip(voiceRaw, 20000));
            record.setVoiceMs(voiceMs);
            if (notes != null && !notes.isEmpty()) {
                String previous = record.getValidationNotes() == null || record.getValidationNotes().isBlank()
                        ? "" : record.getValidationNotes() + "\n";
                record.setValidationNotes(previous + String.join("\n", notes));
            }
            decisionRepository.save(record);
        });
    }

    /** The director should update an existing intent, not create the same one again. */
    static List<DirectorDecision.IntentOp> withoutDuplicates(DirectorDecision d, List<ClientIntent> existing) {
        List<DirectorDecision.IntentOp> kept = new ArrayList<>();
        for (DirectorDecision.IntentOp op : d.intents) {
            ClientIntent same = "CREATE".equals(op.op) && op.parent == null ? sameAs(op, existing) : null;
            if (same == null) {
                kept.add(op);
                continue;
            }
            d.notes.add("intents: " + op.ref + " repeats i" + same.getId() + ", not created again");
            // a question or an alternative that pointed to the repeat now points to the existing intent
            if (d.question != null) {
                d.question.intents.replaceAll(reading -> reading.equals(op.ref) ? "i" + same.getId() : reading);
            }
            for (DirectorDecision.IntentOp other : d.intents) {
                other.alternatives.replaceAll(reading -> reading.equals(op.ref) ? "i" + same.getId() : reading);
            }
        }
        return kept;
    }

    /** An active intent of the same partner and kind with the same reading (and the same words, if any). */
    private static ClientIntent sameAs(DirectorDecision.IntentOp op, List<ClientIntent> existing) {
        String quote = PersianText.loose(op.quote).replace(" ", "");
        String meaning = op.interpretation == null ? "" : op.interpretation.trim().toLowerCase(java.util.Locale.ROOT);
        for (ClientIntent intent : existing) {
            boolean active = intent.getStatus().name().equals("UNCONFIRMED") || intent.getStatus().name().equals("CONFIRMED");
            if (!active || !op.participant.equals(intent.getParticipantRole()) || !op.kind.equals(intent.getKind().name())) {
                continue;
            }
            String theirMeaning = intent.getInterpretation() == null ? "" : intent.getInterpretation().trim().toLowerCase(java.util.Locale.ROOT);
            String theirQuote = PersianText.loose(intent.getQuote()).replace(" ", "");
            if (meaning.equals(theirMeaning) && quote.equals(theirQuote)) {
                return intent;
            }
        }
        return null;
    }

    static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
