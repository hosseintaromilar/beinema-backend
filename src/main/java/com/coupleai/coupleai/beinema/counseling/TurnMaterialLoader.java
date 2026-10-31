package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.CounselingState;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import com.coupleai.coupleai.beinema.Enum.DecisionKind;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.QuestionStatus;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.ClarifyingQuestionRepository;
import com.coupleai.coupleai.beinema.Repository.ClientIntentRepository;
import com.coupleai.coupleai.beinema.Repository.DirectorDecisionRecordRepository;
import com.coupleai.coupleai.beinema.Repository.MessageAttachmentRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.governance.QuestionMarkers;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads the material of one turn from the database (read only). */
@Service
@RequiredArgsConstructor
public class TurnMaterialLoader {

    private static final int QUOTE_HISTORY = 400;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final ClientIntentRepository intentRepository;
    private final ClarifyingQuestionRepository questionRepository;
    private final DirectorDecisionRecordRepository decisionRepository;
    private final CounselingStateStore stateStore;
    private final KnowledgeRetriever retriever;
    private final TurnInbox inbox;
    private final CounselingConfig config;
    private final com.coupleai.coupleai.beinema.Repository.PersonMilestoneRepository milestoneRepository;
    private final com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository chatRoomAgentRepository;

    @Transactional(readOnly = true)
    public TurnMaterial load(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalStateException("Chat room not found: " + roomId));

        // partners
        List<TurnMaterial.Partner> partners = new ArrayList<>();
        Map<Long, String> roleByUserId = new HashMap<>();
        Map<String, String> namesByRole = new HashMap<>();
        Instant now = Instant.now();
        for (ChatRoomParticipant participant : participantRepository.findAllByChatRoom(room)) {
            if (participant.getUser() == null) {
                continue;
            }
            String role = PartnerRole.of(participant.getRole());
            if (roleByUserId.containsValue(role)) {
                // a third member would be ambiguous for OWNER/MEMBER; the first one keeps the role
                continue;
            }
            roleByUserId.put(participant.getUser().getId(), role);
            namesByRole.put(role, participant.getUser().visibleName());
            if (!participant.isActiveMember()) {
                // left the conversation: their earlier messages keep their role, but the work goes on without them
                continue;
            }
            partners.add(new TurnMaterial.Partner(role, participant.getUser().getId(),
                    participant.getUser().visibleName(), status(participant, now)));
        }

        // memory
        CounselingState state = stateStore.load(roomId);
        CounselingSnapshot snapshot = CounselingStateStore.snapshot(state);

        // messages
        List<Message> all = messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(room).stream()
                .filter(MessageTexts::countsAsConversation)
                .toList();
        Map<Long, Message> byId = new LinkedHashMap<>();
        Map<Long, String> roleByMessageId = new HashMap<>();
        for (Message message : all) {
            byId.put(message.getId(), message);
            roleByMessageId.put(message.getId(), message.getSenderType() == MessageSenderType.AGENT
                    ? "COUNSELOR"
                    : roleByUserId.getOrDefault(message.getSenderId(), "PARTNER"));
        }
        List<Message> fresh = fresh(all, state.getLastProcessedMessageId());
        int firstFresh = fresh.isEmpty() ? all.size() : all.indexOf(fresh.get(0));
        List<Message> before = all.subList(0, Math.max(0, firstFresh));
        List<Message> recent = new ArrayList<>(before.subList(Math.max(0, before.size() - config.getRecentMessages()), before.size()));

        Map<String, List<String>> looseTexts = new HashMap<>();
        List<Message> partnerMessages = all.stream().filter(message -> message.getSenderType() == MessageSenderType.USER).toList();
        for (Message message : partnerMessages.subList(Math.max(0, partnerMessages.size() - QUOTE_HISTORY), partnerMessages.size())) {
            String role = roleByUserId.get(message.getSenderId());
            if (role != null) {
                looseTexts.computeIfAbsent(role, key -> new ArrayList<>()).add(PersianText.loose(MessageTexts.visible(message)));
            }
        }

        // files of the visible messages
        Map<Long, List<String>> files = new HashMap<>();
        Set<Long> visibleIds = new java.util.HashSet<>();
        recent.forEach(message -> visibleIds.add(message.getId()));
        fresh.forEach(message -> visibleIds.add(message.getId()));
        for (MessageAttachment attachment : attachmentRepository.findAllByChatRoomIdAndMessageIdIsNotNull(roomId)) {
            if (visibleIds.contains(attachment.getMessageId())) {
                String summary = attachment.getSummary() == null ? "" : " - " + PersianText.clip(attachment.getSummary(), 200);
                files.computeIfAbsent(attachment.getMessageId(), key -> new ArrayList<>())
                        .add(attachment.getOriginalName() + summary);
            }
        }

        // intents, questions, decisions
        List<ClientIntent> intents = intentRepository.findAllByChatRoomIdOrderByIdAsc(roomId);
        List<ClarifyingQuestion> open = questionRepository.findAllByChatRoomIdAndStatus(roomId, QuestionStatus.OPEN);
        List<ClarifyingQuestion> answered = new ArrayList<>();
        for (Message message : fresh) {
            String publicId = QuestionMarkers.answerTo(message.getContent());
            if (publicId != null) {
                questionRepository.findByPublicId(publicId).ifPresent(answered::add);
            }
        }
        int session = room.getSessionNumber() == null ? 1 : room.getSessionNumber();
        long asked = questionRepository.countByChatRoomIdAndSessionNumberAndStatusIn(roomId, session,
                List.of(QuestionStatus.OPEN, QuestionStatus.ANSWERED, QuestionStatus.EXPIRED));
        List<DirectorDecisionRecord> lastTurns = decisionRepository.findByChatRoomIdAndKindOrderByIdDesc(
                roomId, DecisionKind.TURN, PageRequest.of(0, 6));

        // notes waiting for these messages
        Set<Long> freshIds = new java.util.HashSet<>();
        fresh.forEach(message -> freshIds.add(message.getId()));
        List<TurnInbox.Note> notes = inbox.peek(roomId).stream()
                .filter(note -> note.messageId() == null || freshIds.contains(note.messageId()))
                .toList();

        // session clock
        int sessionNumber = room.getSessionNumber() == null ? 1 : room.getSessionNumber();
        int planned = room.getSessionDurationMinutes() == null ? 45 : room.getSessionDurationMinutes();
        long minutes = room.getSessionStartedAt() == null ? 0
                : Math.max(0, Duration.between(room.getSessionStartedAt(), now).toMinutes());
        Integer startSequence = room.getSessionStartSequence();
        long counselorTurns = all.stream()
                .filter(message -> message.getSenderType() == MessageSenderType.AGENT)
                .filter(message -> startSequence == null || message.getSequenceNumber() == null
                        || message.getSequenceNumber() > startSequence)
                .count();

        // safety screen and situation
        List<SafetyScreen.Input> inputs = new ArrayList<>();
        List<String> freshTexts = new ArrayList<>();
        List<String> freshRoles = new ArrayList<>();
        for (Message message : fresh) {
            String text = MessageTexts.visible(message);
            String role = roleByUserId.getOrDefault(message.getSenderId(), "PARTNER");
            inputs.add(new SafetyScreen.Input(message.getId(), role, text));
            freshTexts.add(text);
            freshRoles.add(role);
        }
        SafetyScreen.Result screen = SafetyScreen.screen(inputs);
        List<String> recentRoles = recent.stream().map(message -> roleByMessageId.getOrDefault(message.getId(), "")).toList();
        List<String> answerKinds = answered.stream()
                .map(question -> QuestionOptions.kindOf(question, question.getSelectedOptionId()))
                .toList();
        String speaker = fresh.isEmpty() ? null : roleByUserId.get(fresh.get(fresh.size() - 1).getSenderId());
        Set<String> tags = SituationTags.compute(new SituationTags.Input(
                speaker, freshTexts, freshRoles, recentRoles, snapshot, intents, !open.isEmpty(), answerKinds,
                screen, notes, sessionNumber, minutes, planned, counselorTurns));
        List<KbCard> cards = retriever.retrieve(tags, config.getMaxRuleCards(), config.getMaxRuleChars());
        boolean ownMethod = chatRoomAgentRepository.findActiveAgentBotIds(roomId).stream().anyMatch(config::isOwnMethodWorkflow);
        if (ownMethod) {
            // this companion's method is in its workflow; only safety is shared
            cards = cards.stream().filter(card -> card.type() == KbCard.Type.SAFETY).toList();
            tags = new java.util.HashSet<>(tags);
            tags.add(DirectorPacketBuilder.OWN_METHOD);
        }

        return new TurnMaterial(room, sessionNumber, minutes, planned, partners, recent, fresh, notes, state, snapshot,
                intents, open, answered, asked, lastTurns, screen, tags, cards, roleByUserId, roleByMessageId, looseTexts,
                byId, files, milestoneRepository.findAllByChatRoomIdOrderByIdAsc(roomId),
                chatRoomAgentRepository.findActiveAgentNames(roomId).stream()
                        .filter(name -> name != null && !name.isBlank())
                        .findFirst()
                        .orElse(""),
                namesByRole);
    }

    /** Whether this person wrote again after the given message (their newer words make a reply to the old ones stale). */
    public boolean personWroteAfter(Long roomId, Long userId, Long messageId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null || userId == null || messageId == null) {
            return false;
        }
        return messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(room).stream()
                .anyMatch(message -> message.getSenderType() == MessageSenderType.USER
                        && userId.equals(message.getSenderId())
                        && message.getId() > messageId);
    }

    /** Whether a partner wrote after this message (while the counselor was deciding about it). */
    public boolean someoneWroteAfter(Long roomId, Long messageId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null || messageId == null) {
            return false;
        }
        return messageRepository.findFirstByChatRoomAndDeletedFalseOrderBySequenceNumberDesc(room)
                .filter(last -> last.getSenderType() == MessageSenderType.USER && last.getId() > messageId)
                .isPresent();
    }

    /**
     * Partner messages the director has not decided about yet. A room that has no
     * memory yet (new, or coming from the v2 workflow) starts after the
     * counselor's last message.
     */
    static List<Message> fresh(List<Message> all, Long lastProcessed) {
        List<Message> fresh = new ArrayList<>();
        if (lastProcessed != null) {
            for (Message message : all) {
                if (message.getSenderType() == MessageSenderType.USER && message.getId() > lastProcessed) {
                    fresh.add(message);
                }
            }
            return fresh;
        }
        for (int index = all.size() - 1; index >= 0; index--) {
            Message message = all.get(index);
            if (message.getSenderType() == MessageSenderType.AGENT) {
                break;
            }
            if (message.getSenderType() == MessageSenderType.USER) {
                fresh.add(0, message);
            }
        }
        return fresh;
    }

    private static String status(ChatRoomParticipant participant, Instant now) {
        List<String> parts = new ArrayList<>();
        parts.add(Boolean.FALSE.equals(participant.getSessionConsented()) ? "not consented yet" : "consented");
        if (participant.getPausedUntil() != null && participant.getPausedUntil().isAfter(now)) {
            parts.add("paused for " + Duration.between(now, participant.getPausedUntil()).toSeconds() + "s");
        }
        if (Boolean.TRUE.equals(participant.getReentryPending())) {
            parts.add("returning after a pause");
        }
        return String.join(", ", parts);
    }
}
