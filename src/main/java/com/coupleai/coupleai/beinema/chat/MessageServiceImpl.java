package com.coupleai.coupleai.beinema.chat;

import com.coupleai.coupleai.beinema.files.ChatFileService;
import com.coupleai.coupleai.beinema.session.CounselorSessionService;
import com.coupleai.coupleai.beinema.session.SessionLifecycleService;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.counseling.CounselingConfig;
import com.coupleai.coupleai.beinema.counseling.TurnInbox;
import com.coupleai.coupleai.beinema.counseling.TurnOrchestrator;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import com.coupleai.coupleai.beinema.governance.ReplySegments;
import com.coupleai.coupleai.beinema.governance.WelcomeGuide;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ChatRoomRepository chatRoomRepository;

    private final MessageRepository messageRepository;

    private final ChatRoomParticipantRepository participantRepository;

    private final ChatRoomAgentRepository chatRoomAgentRepository;


    private final ChatRealtimeService chatRealtimeService;

    private final ChatRoomEventHub chatRoomEventHub;

    private final CurrentUserService currentUserService;


    private final CounselorSessionService counselorSessionService;



    private final ParticipationPauseService participationPauseService;

    private final com.coupleai.coupleai.beinema.safety.SafetyCardService safetyCardService;

    private final RoomTurnLock roomTurnLock;

    private final ChatFileService chatFileService;

    private final SessionLifecycleService sessionLifecycleService;

    private final TurnOrchestrator turnOrchestrator;

    private final TurnInbox turnInbox;

    /**
     * Debug only: when the counselor stays silent, show the Director packet that
     * normally stays hidden, so a person testing by hand can see the decision.
     * Off by default (see docs/debug-director-packet.md).
     */
    @Value("${beinema.debug.show-director-packet:false}")
    private boolean debugShowDirectorPacket;

    /** Two page opens this close together are one entry. */
    private static final long ENTRY_REPEAT_WINDOW_MILLIS = 20_000L;

    private final ConcurrentHashMap<String, Long> entryGreetedAt = new ConcurrentHashMap<>();

    /** How long the counselor is seen typing before the first-entry welcome appears. */
    private static final long WELCOME_TYPING_MILLIS = 2500L;
    private static final long TURN_GATHER_MILLIS = 2000;

    /** The room creator's roles: their welcome explains how to invite the partner. */
    private static final java.util.Set<ParticipantRole> ROOM_CREATOR_ROLES =
            java.util.EnumSet.of(ParticipantRole.OWNER, ParticipantRole.PARTNER_A);



    @Override
    public MessageResponse sendMessage(
            Long chatRoomId,
            SendMessageRequest request
    ) {

        throw new UnsupportedOperationException(
                "Use sendMessageStream instead"
        );
    }


    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(
            Long chatRoomId
    ) {

        ChatRoom chatRoom =
                getAuthorizedChatRoom(chatRoomId);

        Long viewerId = currentUserService.getCurrentUser().getId();
        return chatRealtimeService.toResponses(
                chatRoom,
                messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom).stream()
                        .filter(message -> message.getVisibleToUserId() == null
                                || message.getVisibleToUserId().equals(viewerId))
                        .toList()
        );
    }


    @Override
    public void acceptRules(Long chatRoomId) {
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId);
        User user = currentUserService.getCurrentUser();
        participantRepository.findByChatRoomAndUser(chatRoom, user).ifPresent(me -> {
            if (me.getRulesAcceptedAt() == null) {
                me.setRulesAcceptedAt(Instant.now());
                participantRepository.save(me);
            }
        });
        // the page already announced the entry while the rules box was open; greet now
        entryGreetedAt.remove(chatRoom.getId() + ":" + user.getId());
        announceEntry(chatRoomId);
    }

    @Override
    public void announceEntry(Long chatRoomId) {
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId);
        User user = currentUserService.getCurrentUser();
        // the first session waits while someone who just opened the room reads the rules
        sessionLifecycleService.markEntered(chatRoom, user);
        Long agentId = resolveAgentId(chatRoom.getId());
        // the clock first: a session that is over is closed before anyone is greeted into it
        chatRoom = sessionLifecycleService.refresh(chatRoom);
        boolean sessionOpen = sessionLifecycleService.isActive(chatRoom);

        List<ChatRoomParticipant> everyone = participantRepository.findAllByChatRoom(chatRoom);
        ChatRoomParticipant me = everyone.stream()
                .filter(item -> item.getUser() != null && item.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElse(null);
        if (chatRoom.getStatus() == ChatRoomStatus.ENDED || (me != null && !me.isActiveMember())) {
            // a finished conversation, or someone who left it: they read, nobody greets or waits for them
            return;
        }
        // who left the conversation is not waited for, greeted with or counted
        List<ChatRoomParticipant> members = ChatRoomParticipant.active(everyone);
        List<Message> history = messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom).stream()
                .filter(message -> message.getVisibleToUserId() == null)
                .toList();
        boolean fresh = history.stream().noneMatch(message ->
                message.getSenderType() == MessageSenderType.USER
                        || message.getSenderType() == MessageSenderType.AGENT
        );
        boolean ended = chatRoom.getStatus() == ChatRoomStatus.ENDED
                || chatRoom.getStatus() == ChatRoomStatus.ARCHIVED
                || chatRoom.getStatus() == ChatRoomStatus.DELETED;
        boolean consented = me == null || !Boolean.FALSE.equals(me.getSessionConsented());
        boolean partnerPending = members.stream().anyMatch(item ->
                item.getUser() != null
                        && !item.getUser().getId().equals(user.getId())
                        && Boolean.FALSE.equals(item.getSessionConsented())
        );
        boolean held = me != null && (
                Boolean.TRUE.equals(me.getReentryPending())
                        || (me.getPausedUntil() != null && me.getPausedUntil().isAfter(Instant.now()))
        );
        boolean bothReady = members.size() >= 2 && members.stream()
                .allMatch(item -> !Boolean.FALSE.equals(item.getSessionConsented()));

        String claimKey = chatRoom.getId() + ":" + user.getId();
        long now = System.currentTimeMillis();
        Long previousClaim = entryGreetedAt.put(claimKey, now);
        if (previousClaim != null && now - previousClaim < ENTRY_REPEAT_WINDOW_MILLIS) {
            return;
        }

        String others = members.stream()
                .filter(item -> item.getUser() != null && !item.getUser().getId().equals(user.getId()))
                .map(item -> item.getUser().visibleName())
                .reduce((left, right) -> left + "، " + right)
                .orElse("");
        String names = members.stream()
                .map(item -> item.getUser() == null ? "" : item.getUser().visibleName())
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + " و " + right)
                .orElse("");

        /*
         * First entry of this person. The rules were accepted before the chat
         * (the checkbox on the create-room or invitation page, or the rules box
         * on the chat page), so the counselor only welcomes them briefly and the
         * entry counts as the consent: the counselor then asks the opening
         * question. Someone who has not accepted the rules yet sees the rules box
         * on the chat page; nothing is said to them until they accept.
         */
        boolean needsConsent = me != null
                && me.getUser() != null
                && Boolean.FALSE.equals(me.getSessionConsented());
        if (needsConsent) {
            if (me.getRulesAcceptedAt() == null) {
                return;
            }
            {
                String welcome = WelcomeGuide.shortWelcome(new WelcomeGuide.Facts(
                        user.visibleName(),
                        chatRoomAgentRepository.findActiveAgentNames(chatRoom.getId()).stream()
                                .filter(name -> name != null && !name.isBlank())
                                .findFirst()
                                .orElse(""),
                        ROOM_CREATOR_ROLES.contains(me.getRole()),
                        others,
                        history.stream().anyMatch(message -> message.getSenderType() == MessageSenderType.USER),
                        sessionLifecycleService.plannedMinutes(chatRoom),
                        sessionLifecycleService.breakMinutes(),
                        counselorSessionService.isRelationshipCounselor(chatRoom),
                        sessionLifecycleService.overtimeMinutes(),
                        sessionLifecycleService.maxSessions()
                ));
                // like the counselor's other messages: a short "typing" pause first
                chatRealtimeService.publishAgentTyping(chatRoom.getId(), WELCOME_TYPING_MILLIS);
                sleepQuietly(WELCOME_TYPING_MILLIS);
                // the creator alone in a new room answers on a card: alone, or with the partner
                boolean asksMode = ROOM_CREATOR_ROLES.contains(me.getRole()) && others.isEmpty()
                        && chatRoom.getSessionMode() == null;
                chatRealtimeService.saveAgentAndPublish(chatRoom.getId(), agentId, asksMode
                        ? com.coupleai.coupleai.beinema.governance.KitMarkers.content(
                                com.coupleai.coupleai.beinema.governance.KitMarkers.MODE, "mode-" + chatRoom.getId(), welcome)
                        : welcome);
            }
            participationPauseService.acceptSessionConsent(chatRoom, user);
            return;
        }

        // between sessions nobody is greeted; the counselor speaks when someone enters a session, not on every page open
    }


    @Override
    public SseEmitter subscribe(
            Long chatRoomId
    ) {

        ChatRoom chatRoom =
                getAuthorizedChatRoom(chatRoomId);

        return chatRoomEventHub.subscribe(chatRoom.getId(), currentUserService.getCurrentUser().getId());
    }


    @Override
    @Transactional
    public void submitReentryCommitment(
            Long chatRoomId,
            boolean accepted
    ) {
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId);
        User user = currentUserService.getCurrentUser();
        if (accepted) {
            participationPauseService.acceptReentry(chatRoom, user);
            return;
        }
        participationPauseService.declineReentry(chatRoom, user);
    }


    @Override
    @Transactional
    public void submitSessionConsent(
            Long chatRoomId,
            boolean accepted
    ) {
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId);
        User user = currentUserService.getCurrentUser();
        if (accepted) {
            participationPauseService.acceptSessionConsent(chatRoom, user);
            return;
        }
        participationPauseService.declineSessionConsent(chatRoom, user);
    }


    @Override
    public SseEmitter sendMessageStream(
            Long chatRoomId,
            SendMessageRequest request
    ) {

        /*
         * 1. Validate chat room access.
         */

        ChatRoom chatRoom =
                getAuthorizedChatRoom(chatRoomId);


        /*
         * 2. Get current user.
         */

        User user =
                currentUserService.getCurrentUser();


        /*
         * 3. Validate request.
         */

        boolean hasFiles = request != null
                && request.getAttachmentIds() != null
                && !request.getAttachmentIds().isEmpty();

        if (
                request == null
                        ||
                        (!hasFiles
                                && (request.getContent() == null || request.getContent().isBlank()))
        ) {

            throw new RuntimeException(
                    "Message content cannot be empty"
            );
        }


        chatRoom = sessionLifecycleService.refresh(chatRoom);
        sessionLifecycleService.requireCanWrite(chatRoom, user);

        return orchestratedStream(chatRoom, user, request);
    }

    /**
     * v3 (beinema.brain.mode=orchestrated): the message is stored and shown at
     * once; the counselor's turn (director, memory, voice) runs in the
     * background, in line with the room's other turns. Messages that arrive
     * while a turn is running are decided together in the next turn.
     */
    private SseEmitter orchestratedStream(ChatRoom chatRoom, User user, SendMessageRequest request) {
        ParticipationPauseService.ResumeHint resumeHint = participationPauseService.requireCanSend(chatRoom, user);
        int nextSequence = messageRepository.findMaxSequenceNumberByChatRoom(chatRoom).orElse(0) + 1;
        Message replyTarget = resolveReplyTarget(chatRoom, request.getReplyToMessageId());
        final List<MessageAttachment> files = chatFileService.prepare(
                chatRoom.getId(), user.getId(), request.getAttachmentIds());
        final String captionText = request.getContent() == null ? "" : request.getContent();

        Message userMessage = Message.builder()
                .chatRoom(chatRoom)
                .senderType(MessageSenderType.USER)
                .senderId(user.getId())
                .content(captionText)
                .replyToMessageId(replyTarget == null ? null : replyTarget.getId())
                .status(MessageStatus.SENT)
                .sequenceNumber(nextSequence)
                .build();
        messageRepository.saveAndFlush(userMessage);
        chatFileService.link(files, userMessage.getId());
        chatRealtimeService.publish(chatRoom.getId(), userMessage);
        // safety never waits for the model
        safetyCardService.check(chatRoom, user, captionText);
        // the first message can be what starts the clock
        sessionLifecycleService.refresh(chatRoom);

        final Long roomId = chatRoom.getId();
        if (resumeHint != null && resumeHint.handshake()) {
            String role = participantRepository.findByChatRoomAndUser(chatRoom, user)
                    .map(participant -> toStableRole(participant.getRole()))
                    .orElse("MEMBER");
            turnInbox.add(roomId, userMessage.getId(),
                    "[SYSTEM | SESSION] REENTRY_COMMITTED for " + role + "; PAUSE_EXPIRED for " + role);
        }

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        emitter.onCompletion(() -> emitterClosed.set(true));
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            emitter.complete();
        });
        emitter.onError(error -> emitterClosed.set(true));

        SecurityContext securityContext = SecurityContextHolder.getContext();
        new Thread(() -> {
            SecurityContextHolder.setContext(securityContext);
            // messages sent one after another go into the same turn
            sleepQuietly(TURN_GATHER_MILLIS);
            String key = CounselingConfig.lockKey(roomId);
            roomTurnLock.lock(key);
            try {
                turnInbox.add(roomId, userMessage.getId(),
                        chatFileService.describeForBrain(files, !captionText.isBlank()));
                if (!sessionLifecycleService.pendingResume(roomId).isEmpty()) {
                    // v3 carries the memory in every turn; the old resume brief is not needed
                    sessionLifecycleService.resumeDelivered(roomId);
                }
                TurnOrchestrator.Outcome outcome = turnOrchestrator.runTurn(roomId);
                finishEmitter(emitter, emitterClosed, !outcome.failed());
                turnOrchestrator.deliverRemaining(outcome);
            } catch (Exception exception) {
                exception.printStackTrace();
                finishEmitter(emitter, emitterClosed, false);
            } finally {
                roomTurnLock.unlock(key);
                SecurityContextHolder.clearContext();
            }
        }).start();
        return emitter;
    }

    private static void finishEmitter(SseEmitter emitter, AtomicBoolean emitterClosed, boolean ok) {
        if (!emitterClosed.get()) {
            try {
                emitter.send(ok
                        ? SseEmitter.event().name("done").data("DONE")
                        : SseEmitter.event().name("error").data(com.coupleai.coupleai.beinema.ai.AiUnavailableException.TEXT));
            } catch (IOException | IllegalStateException closed) {
                emitterClosed.set(true);
            }
        }
        if (emitterClosed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }


    private ChatRoom getAuthorizedChatRoom(
            Long chatRoomId
    ) {

        User user =
                currentUserService.getCurrentUser();


        ChatRoom chatRoom =
                chatRoomRepository
                        .findById(chatRoomId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Chat room not found"
                                )
                        );


        boolean isParticipant =
                participantRepository
                        .existsByChatRoomAndUser(
                                chatRoom,
                                user
                        );


        if (!isParticipant) {

            throw new RuntimeException(
                    "Access denied"
            );
        }


        return chatRoom;
    }


    private Long resolveAgentId(Long chatRoomId) {

        return chatRoomAgentRepository
                .findActiveAgentIds(chatRoomId)
                .stream()
                .findFirst()
                .orElse(0L);
    }


    /** A reply target must exist, be visible, and belong to this room. */
    private Message resolveReplyTarget(ChatRoom chatRoom, Long replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }
        return messageRepository.findById(replyToMessageId)
                .filter(candidate -> candidate.getChatRoom() != null
                        && chatRoom.getId().equals(candidate.getChatRoom().getId())
                        && !Boolean.TRUE.equals(candidate.getDeleted())
                        && candidate.getSenderType() != MessageSenderType.SYSTEM)
                .orElse(null);
    }



    /**
     * Typing time before a follow-up message: proportional to its length with a
     * little irregularity, plus the pause hint the counselor wrote. The text and
     * the rhythm come from the counselor; this only turns them into milliseconds.
     */
    public static long pacingMillis(ReplySegments.Segment segment) {
        int length = segment.isSticker() ? 12 : segment.text().length();
        long typing = 500L + length * 38L;
        typing = Math.min(5200L, Math.max(1200L, typing));
        double jitter = 0.8 + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.5;
        long total = Math.round(typing * jitter) + segment.pauseSeconds() * 700L;
        return Math.min(7000L, total);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }



    private String toStableRole(ParticipantRole role) {

        if (role == null) {
            return "MEMBER";
        }

        if (role == ParticipantRole.OWNER || role == ParticipantRole.PARTNER_A) {
            return "OWNER";
        }

        if (role == ParticipantRole.MEMBER || role == ParticipantRole.PARTNER_B) {
            return "MEMBER";
        }

        return role.name();
    }
}
