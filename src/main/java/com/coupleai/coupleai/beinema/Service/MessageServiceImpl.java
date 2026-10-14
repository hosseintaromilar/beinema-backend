package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
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
import com.coupleai.coupleai.beinema.governance.EntryGreeting;
import com.coupleai.coupleai.beinema.governance.GovernanceDecision;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
import com.coupleai.coupleai.beinema.governance.GovStreamBuffer;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import com.coupleai.coupleai.beinema.governance.CounselorSignal;
import com.coupleai.coupleai.beinema.governance.ReplySegments;
import com.coupleai.coupleai.beinema.governance.ReentryCommitment;
import lombok.RequiredArgsConstructor;
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

    private final ConcurrentHashMap<String, Long> entryGreetedAt = new ConcurrentHashMap<>();



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

        return chatRealtimeService.toResponses(
                chatRoom,
                messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom)
        );
    }


    @Override
    public void announceEntry(Long chatRoomId) {
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId);
        User user = currentUserService.getCurrentUser();
        Long agentId = resolveAgentId(chatRoom.getId());
        if (!agentAccessService.hasValidAccess(chatRoom.getId(), agentId)) {
            return;
        }

        List<ChatRoomParticipant> members = participantRepository.findAllByChatRoom(chatRoom);
        ChatRoomParticipant me = members.stream()
                .filter(item -> item.getUser() != null && item.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElse(null);
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
        String spoken = counselorSessionService.requestEntry(
                chatRoom.getAiConversationId(),
                cue
        );
        if (spoken == null) {
            spoken = "";
        }
        boolean needsConsent = me != null
                && me.getUser() != null
                && Boolean.FALSE.equals(me.getSessionConsented());
        boolean consentAlreadyOffered = history.stream().anyMatch(message ->
                ReentryCommitment.KIND_CONSENT.equals(ReentryCommitment.kind(message.getContent()))
                        && user.getId().equals(ReentryCommitment.userId(message.getContent()))
        );
        if (needsConsent && !consentAlreadyOffered) {
            spoken = ReentryCommitment.consentMarker(user.getId()) + "\n" + spoken;
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

        return chatRoomEventHub.subscribe(chatRoom.getId());
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

        if (
                request == null
                        ||
                        request.getContent() == null
                        ||
                        request.getContent().isBlank()
        ) {

            throw new RuntimeException(
                    "Message content cannot be empty"
            );
        }


        Long roomAgentId = resolveAgentId(chatRoom.getId());
        agentAccessService.requireValidAccess(chatRoom.getId(), roomAgentId);
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

        Message userMessage =
                Message.builder()
                        .chatRoom(chatRoom)
                        .senderType(MessageSenderType.USER)
                        .senderId(user.getId())
                        .content(request.getContent())
                        .replyToMessageId(replyTarget == null ? null : replyTarget.getId())
                        .status(MessageStatus.SENT)
                        .sequenceNumber(nextSequence)
                        .build();


        messageRepository.saveAndFlush(userMessage);
        chatRealtimeService.publish(chatRoom.getId(), userMessage);

        chatRoom = counselorSessionService.ensureBound(chatRoom, user);
        String conversationId = chatRoom.getAiConversationId();
        Long chatRoomPk = chatRoom.getId();
        String userContent = labeledUserContent(chatRoom, user, request.getContent(), resumeHint, replyTarget);
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

        String roster = participantRepository.findAllByChatRoom(chatRoom)
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
    static long pacingMillis(ReplySegments.Segment segment) {
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
