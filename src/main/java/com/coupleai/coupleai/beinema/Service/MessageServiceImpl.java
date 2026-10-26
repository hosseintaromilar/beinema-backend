package com.coupleai.coupleai.beinema.Service;

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
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.counseling.CounselingConfig;
import com.coupleai.coupleai.beinema.counseling.TurnInbox;
import com.coupleai.coupleai.beinema.counseling.TurnOrchestrator;
import com.coupleai.coupleai.beinema.governance.EntryGreeting;
import com.coupleai.coupleai.beinema.governance.GovernanceDecision;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
import com.coupleai.coupleai.beinema.governance.GovStreamBuffer;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import com.coupleai.coupleai.beinema.governance.CounselorSignal;
import com.coupleai.coupleai.beinema.governance.ReplySegments;
import com.coupleai.coupleai.beinema.governance.ReentryCommitment;
import com.coupleai.coupleai.beinema.governance.WelcomeGuide;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final UserRepository userRepository;

    private final ChatRealtimeService chatRealtimeService;

    private final ChatRoomEventHub chatRoomEventHub;

    private final CurrentUserService currentUserService;

    private final MetisService metisService;

    private final CounselorSessionService counselorSessionService;

    private final ObjectMapper objectMapper;

    private final AgentAccessService agentAccessService;

    private final ParticipationPauseService participationPauseService;

    private final SessionMemoryService sessionMemoryService;

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

    private final ConcurrentHashMap<String, Long> entryGreetedAt = new ConcurrentHashMap<>();

    /** How long the counselor is seen typing before the first-entry welcome appears. */
    private static final long WELCOME_TYPING_MILLIS = 2500L;

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
    public void announceEntry(Long chatRoomId) {
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId);
        User user = currentUserService.getCurrentUser();
        // the first session waits while someone who just opened the room reads the rules
        sessionLifecycleService.markEntered(chatRoom, user);
        Long agentId = resolveAgentId(chatRoom.getId());
        if (!agentAccessService.hasValidAccess(chatRoom.getId(), agentId)) {
            return;
        }
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
        List<Message> history = messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom);
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
        if (previousClaim != null && now - previousClaim < EntryGreeting.REPEAT_WINDOW_MILLIS) {
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
         * First entry of this person: the complete, fixed welcome (who the
         * counselor is, how Beinema works and its framework, how to invite the
         * partner, the rules and safety) with the consent card. Only once; until
         * they agree, that card is all they need, so nothing else is said. Also
         * between two sessions: someone new must be able to agree before the start button.
         */
        boolean needsConsent = me != null
                && me.getUser() != null
                && Boolean.FALSE.equals(me.getSessionConsented());
        if (needsConsent) {
            boolean consentAlreadyOffered = history.stream().anyMatch(message ->
                    ReentryCommitment.KIND_CONSENT.equals(ReentryCommitment.kind(message.getContent()))
                            && user.getId().equals(ReentryCommitment.userId(message.getContent()))
            );
            if (!consentAlreadyOffered) {
                String welcome = WelcomeGuide.firstEntry(new WelcomeGuide.Facts(
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
                chatRealtimeService.saveAgentAndPublish(
                        chatRoom.getId(),
                        agentId,
                        ReentryCommitment.consentMarker(user.getId()) + "\n" + welcome
                );
            }
            return;
        }

        if (!sessionOpen || counselorSessionService.usesNewBrain(chatRoom)) {
            // between sessions nobody is greeted; v3 speaks when someone enters a session, not on every page open
            return;
        }
        chatRoom = counselorSessionService.ensureBound(chatRoom, user);
        boolean thisPersonHasSpoken = history.stream().anyMatch(message ->
                message.getSenderType() == MessageSenderType.USER
                        && user.getId().equals(message.getSenderId())
        );
        long agentTurns = history.stream()
                .filter(message -> message.getSenderType() == MessageSenderType.AGENT)
                .count();
        boolean sessionAlreadyOpen = history.stream()
                .anyMatch(message -> message.getSenderType() == MessageSenderType.USER)
                || agentTurns >= 2;
        String cue;
        if (!fresh && sessionAlreadyOpen && !thisPersonHasSpoken) {
            cue = EntryGreeting.participantJoined(
                    user.visibleName(),
                    consented,
                    recentTranscript(history)
            );
        } else if (bothReady && fresh) {
            cue = EntryGreeting.sessionReady(names);
        } else {
            cue = EntryGreeting.instruction(
                    user.visibleName(),
                    others,
                    fresh,
                    ended,
                    consented,
                    partnerPending,
                    held,
                    recentTranscript(history)
            );
        }
        String spoken = counselorSessionService.speakEvent(chatRoom, user, cue);
        if (spoken == null) {
            spoken = "";
        }
        if (!CounselorSessionService.isSilentWait(spoken)) {
            chatRealtimeService.saveAgentAndPublish(chatRoom.getId(), agentId, spoken);
        }
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

        Long roomAgentId = resolveAgentId(chatRoom.getId());
        agentAccessService.requireValidAccess(chatRoom.getId(), roomAgentId);
        if (counselorSessionService.usesNewBrain(chatRoom)) {
            return orchestratedStream(chatRoom, user, request);
        }
        chatRoom = counselorSessionService.ensureBound(chatRoom, user);
        if (
                chatRoom.getAiConversationId() == null
                        ||
                        chatRoom.getAiConversationId().isBlank()
        ) {

            throw new RuntimeException(
                    "AI conversation has not been initialized"
            );
        }

        ParticipationPauseService.ResumeHint resumeHint =
                participationPauseService.requireCanSend(chatRoom, user);



        /*
         * 5. Find next sequence.
         */

        int nextSequence =
                messageRepository
                        .findMaxSequenceNumberByChatRoom(chatRoom)
                        .orElse(0)
                        + 1;


        /*
         * 6. Save USER message.
         */

        Message replyTarget = resolveReplyTarget(chatRoom, request.getReplyToMessageId());
        final List<MessageAttachment> files = chatFileService.prepare(
                chatRoom.getId(), user.getId(), request.getAttachmentIds());
        final String captionText = request.getContent() == null ? "" : request.getContent();

        Message userMessage =
                Message.builder()
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

        chatRoom = counselorSessionService.ensureBound(chatRoom, user);
        String conversationId = chatRoom.getAiConversationId();
        Long chatRoomPk = chatRoom.getId();
        String baseUserContent = labeledUserContent(chatRoom, user, captionText, resumeHint, replyTarget);
        Long agentId = resolveAgentId(chatRoomPk);


        /*
         * 7. Create SSE emitter.
         */

        SseEmitter emitter =
                new SseEmitter(
                        5 * 60 * 1000L
                );


        /*
         * This flag tells us whether the SSE
         * connection has already been closed.
         */
        AtomicBoolean emitterClosed =
                new AtomicBoolean(false);


        /*
         * Handle normal completion.
         */

        emitter.onCompletion(() ->
                emitterClosed.set(true)
        );


        /*
         * Handle timeout.
         */

        emitter.onTimeout(() -> {

            emitterClosed.set(true);

            emitter.complete();

        });


        /*
         * Handle emitter error.
         */

        emitter.onError(error ->
                emitterClosed.set(true)
        );


        /*
         * 8. Start asynchronous AI streaming.
         */

        SecurityContext securityContext =
                SecurityContextHolder.getContext();

        new Thread(() -> {

            SecurityContextHolder.setContext(securityContext);

            StringBuilder aiResponse =
                    new StringBuilder();
            GovStreamBuffer streamBuffer =
                    new GovStreamBuffer();

            /*
             * One Metis turn per room at a time. Messages are already saved;
             * only their processing waits in line.
             */
            roomTurnLock.lock(conversationId);

            /*
             * Files are read by a neutral reader; the brain receives that reading
             * as text, together with whether the person also wrote a message.
             */
            final String resumeBlock = sessionLifecycleService.pendingResume(chatRoomPk);
            final String userContent = resumeBlock
                    + baseUserContent
                    + chatFileService.describeForBrain(files, !captionText.isBlank());


            try {

                try {

                    metisService.streamMessage(

                            conversationId,

                            userContent,

                            chunk -> {

                                String visibleChunk =
                                        streamBuffer.accept(chunk);

                                if (
                                        visibleChunk == null
                                                ||
                                                visibleChunk.isEmpty()
                                ) {

                                    return;
                                }

                                /*
                                 * The reply is delivered as one or more
                                 * separate messages after the whole turn is
                                 * known, so chunks are not streamed raw.
                                 */
                                aiResponse.append(visibleChunk);
                            }
                    );

                } catch (Exception streamError) {

                    if (aiResponse.length() == 0) {
                        String fallback = metisService.sendMessageAllowBlank(
                                conversationId,
                                userContent
                        );
                        String visibleFallback = streamBuffer.accept(
                                fallback == null ? "" : fallback
                        );
                        if (visibleFallback != null && !visibleFallback.isEmpty()) {
                            aiResponse.append(visibleFallback);
                        }
                    } else {

                        /*
                         * Metis broke after sending part of the
                         * answer. Keep what already arrived so the
                         * governance decision inside it is still
                         * enforced instead of being lost.
                         */

                        streamError.printStackTrace();
                    }
                }


                if (!resumeBlock.isEmpty()) {
                    sessionLifecycleService.resumeDelivered(chatRoomPk);
                }

                GovernanceDecision decision = streamBuffer.finish();
                try {
                    ChatRoom liveRoom = chatRoomRepository.findById(chatRoomPk).orElse(null);
                    if (liveRoom != null) {
                        participationPauseService.apply(liveRoom, decision);
                    }
                } catch (Exception governanceError) {
                    governanceError.printStackTrace();
                }

                String counselorReply = decision.visibleText();
                try {
                    sessionMemoryService.store(chatRoomPk, streamBuffer.raw());
                } catch (Exception ignored) {
                    // Persistence must not block delivery.
                }

                ReplySegments.Parsed parsed =
                        CounselorSessionService.isSilentWait(counselorReply)
                                ? new ReplySegments.Parsed(List.of(), false)
                                : ReplySegments.parse(counselorReply);
                List<ReplySegments.Segment> segments = parsed.segments();
                final Long triggerId = userMessage.getId();
                final boolean replyFirst = parsed.replyToTrigger();

                /*
                 * The first message goes out at once. The rest, if the
                 * counselor chose to speak in several messages, follow below
                 * with natural typing pauses once the sender is released.
                 */
                if (!segments.isEmpty()) {
                    deliver(chatRoomPk, agentId, segments.get(0), triggerId, replyFirst);
                } else {
                    /*
                     * The counselor chose silence. If the brain attached a
                     * signal to that silence (a reaction or a sticker), only
                     * execute it; the meaning was decided in Metis.
                     */
                    if (debugShowDirectorPacket) {
                        String packet = streamBuffer.raw();
                        if (packet != null && !packet.isBlank()) {
                            chatRealtimeService.saveAgentAndPublish(
                                    chatRoomPk,
                                    agentId,
                                    "🔧 DIR (سکوت): " + packet.replace('\n', ' ').trim()
                            );
                        }
                    }
                    CounselorSignal signal = CounselorSignal.read(streamBuffer.raw());
                    if (signal.reactionToken() != null) {
                        chatRealtimeService.reactAndPublish(
                                chatRoomPk,
                                userMessage.getId(),
                                signal.reactionToken()
                        );
                    } else if (signal.stickerToken() != null) {
                        chatRealtimeService.saveStickerAndPublish(
                                chatRoomPk,
                                agentId,
                                signal.stickerToken(),
                                userMessage.getId()
                        );
                    }
                }


                /*
                 * 10. Send DONE only if SSE
                 * connection is still alive.
                 */

                if (
                        !emitterClosed.get()
                ) {

                    try {

                        emitter.send(
                                SseEmitter.event()
                                        .name("done")
                                        .data("DONE")
                        );

                    } catch (
                            IOException
                            |
                            IllegalStateException e
                    ) {

                        emitterClosed.set(true);
                    }
                }


                /*
                 * 11. Complete only if still open.
                 */

                if (
                        emitterClosed.compareAndSet(
                                false,
                                true
                        )
                ) {

                    emitter.complete();
                }


                /*
                 * 12. Remaining messages of the same counselor turn.
                 * The sender is already released, so everyone can keep
                 * typing; the counselor simply keeps talking.
                 */

                for (int index = 1; index < segments.size(); index++) {

                    ReplySegments.Segment segment = segments.get(index);
                    long typingMillis = pacingMillis(segment);

                    chatRealtimeService.publishAgentTyping(chatRoomPk, typingMillis);
                    sleepQuietly(typingMillis);
                    deliver(chatRoomPk, agentId, segment, triggerId, false);
                }


            } catch (Exception e) {

                e.printStackTrace();


                /*
                 * If the connection is still alive,
                 * notify frontend.
                 */

                if (
                        !emitterClosed.get()
                ) {

                    try {

                        emitter.send(
                                SseEmitter.event()
                                        .name("error")
                                        .data(
                                                "خطا در دریافت پاسخ از هوش مصنوعی"
                                        )
                        );

                    } catch (
                            IOException
                            |
                            IllegalStateException ignored
                    ) {

                        emitterClosed.set(true);
                    }
                }


                /*
                 * Never call completeWithError()
                 * after SSE has started.
                 */

                if (
                        emitterClosed.compareAndSet(
                                false,
                                true
                        )
                ) {

                    emitter.complete();
                }
            } finally {
                roomTurnLock.unlock(conversationId);
                SecurityContextHolder.clearContext();
            }

        }).start();


        /*
         * 12. Return SSE immediately.
         */

        return emitter;
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
                        : SseEmitter.event().name("error").data("خطا در دریافت پاسخ از هوش مصنوعی"));
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

    /** Sends one segment of the counselor's turn: a text message or a sticker. */
    private void deliver(
            Long chatRoomId,
            Long agentId,
            ReplySegments.Segment segment,
            Long triggerMessageId,
            boolean replyToTrigger
    ) {
        if (segment.isSticker()) {
            chatRealtimeService.saveStickerAndPublish(
                    chatRoomId,
                    agentId,
                    segment.stickerToken(),
                    triggerMessageId
            );
            return;
        }
        chatRealtimeService.saveAgentAndPublish(
                chatRoomId,
                agentId,
                segment.text(),
                replyToTrigger ? triggerMessageId : null
        );
    }

    private String labeledUserContent(
            ChatRoom chatRoom,
            User user,
            String content,
            ParticipationPauseService.ResumeHint resumeHint,
            Message replyTarget
    ) {

        ChatRoomParticipant participant =
                participantRepository
                        .findByChatRoomAndUser(chatRoom, user)
                        .orElse(null);

        String role = "OWNER";
        if (participant != null) {
            role = toStableRole(participant.getRole());
        }

        String roster = ChatRoomParticipant.active(participantRepository.findAllByChatRoom(chatRoom))
                .stream()
                .map(item -> toStableRole(item.getRole())
                        + "="
                        + item.getUser().visibleName())
                .reduce((left, right) -> left + "; " + right)
                .orElse(role + "=" + user.visibleName());

        boolean speakerConsented = participant != null
                && Boolean.TRUE.equals(participant.getSessionConsented());

        String quoted = "";
        if (replyTarget != null) {
            String who = replyTarget.getSenderType() == MessageSenderType.AGENT
                    ? "COUNSELOR"
                    : userRepository.findById(replyTarget.getSenderId())
                            .map(User::visibleName)
                            .orElse("PARTICIPANT");
            String excerpt = CounselorSignal.isSticker(replyTarget.getContent())
                    ? "(sticker)"
                    : CounselorSignal.stripSticker(replyTarget.getContent());
            if (excerpt.length() > 200) {
                excerpt = excerpt.substring(0, 200) + "...";
            }
            quoted = "[REPLYING TO | " + who + "]\n" + excerpt.replace('\n', ' ') + "\n";
        }
        String labeled = "[PARTICIPANTS: " + roster + "]\n"
                + "[" + role + " | " + user.visibleName() + "]\n"
                + quoted
                + content;
        String sessionNote = "";
        if (speakerConsented) {
            sessionNote = "[SYSTEM | SESSION]\n"
                    + "CONSENT_RECORDED for " + role + ".\n";
        }
        if (resumeHint != null && resumeHint.handshake()) {
            sessionNote = sessionNote
                    + "[SYSTEM | SESSION]\nREENTRY_COMMITTED for " + role + "\nPAUSE_EXPIRED for " + role + "\n";
        }
        return sessionNote + labeled;
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


    private String recentTranscript(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        int from = Math.max(0, history.size() - 12);
        StringBuilder transcript = new StringBuilder();
        for (Message message : history.subList(from, history.size())) {
            if (message.getSenderType() == MessageSenderType.SYSTEM) {
                continue;
            }
            String visible = new GovernancePacketParser().strip(
                    ReentryCommitment.visible(message.getContent())
            );
            if (visible.isBlank()) {
                continue;
            }
            if (visible.length() > 280) {
                visible = visible.substring(0, 280);
            }
            String speaker = message.getSenderType() == MessageSenderType.AGENT
                    ? "مشاور"
                    : "مراجع";
            transcript.append(speaker).append(": ").append(visible).append('\n');
        }
        return transcript.toString();
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
