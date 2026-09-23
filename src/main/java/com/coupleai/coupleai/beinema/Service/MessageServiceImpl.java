package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.governance.ConsentReplay;
import com.coupleai.coupleai.beinema.governance.GovernanceDecision;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
import com.coupleai.coupleai.beinema.governance.GovStreamBuffer;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
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

        Message userMessage =
                Message.builder()
                        .chatRoom(chatRoom)
                        .senderType(MessageSenderType.USER)
                        .senderId(user.getId())
                        .content(request.getContent())
                        .status(MessageStatus.SENT)
                        .sequenceNumber(nextSequence)
                        .build();


        messageRepository.saveAndFlush(userMessage);
        chatRealtimeService.publish(chatRoom.getId(), userMessage);

        chatRoom = counselorSessionService.ensureBound(chatRoom, user);
        String conversationId = chatRoom.getAiConversationId();
        Long chatRoomPk = chatRoom.getId();
        String userContent = labeledUserContent(chatRoom, user, request.getContent(), resumeHint);
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

                                aiResponse.append(visibleChunk);

                                if (emitterClosed.get()) {
                                    return;
                                }

                                try {

                                    emitter.send(
                                            SseEmitter.event()
                                                    .name("message")
                                                    .data(
                                                            objectMapper.writeValueAsString(visibleChunk),
                                                            MediaType.TEXT_PLAIN
                                                    )
                                    );

                                } catch (
                                        IOException
                                        |
                                        IllegalStateException e
                                ) {

                                    emitterClosed.set(true);

                                }
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
                            if (!emitterClosed.get()) {
                                try {
                                    emitter.send(
                                            SseEmitter.event()
                                                    .name("message")
                                                    .data(
                                                            objectMapper.writeValueAsString(visibleFallback),
                                                            MediaType.TEXT_PLAIN
                                                    )
                                    );
                                } catch (
                                        IOException
                                        |
                                        IllegalStateException e
                                ) {
                                    emitterClosed.set(true);
                                }
                            }
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
                        ParticipationPauseService.ApplyResult applied =
                                participationPauseService.apply(liveRoom, decision);
                        if (applied.pauseEnforced()) {
                            sendCalmDownFollowUp(
                                    liveRoom,
                                    conversationId,
                                    agentId,
                                    applied
                            );
                        }
                    }
                } catch (Exception governanceError) {
                    governanceError.printStackTrace();
                }

                String counselorReply = decision.visibleText();
                if (ConsentReplay.asksAgain(counselorReply)) {
                    counselorReply = repairConsentReplay(
                            conversationId,
                            request.getContent()
                    );
                }
                if (decision.governanceAction() != null
                        && decision.governanceAction().changesParticipation()
                        && CounselorSessionService.isSilentWait(counselorReply)) {
                    counselorReply = "برای آرام شدن فضای گفتگو، ارسال پیام برای مدت کوتاهی متوقف شد.";
                    if (aiResponse.length() == 0 && !emitterClosed.get()) {
                        try {
                            emitter.send(
                                    SseEmitter.event()
                                            .name("message")
                                            .data(
                                                    objectMapper.writeValueAsString(counselorReply),
                                                    MediaType.TEXT_PLAIN
                                            )
                            );
                        } catch (IOException | IllegalStateException ignored) {
                            emitterClosed.set(true);
                        }
                    }
                }

                if (!CounselorSessionService.isSilentWait(counselorReply)) {
                    chatRealtimeService.saveAgentAndPublish(
                            chatRoomPk,
                            agentId,
                            counselorReply
                    );
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


    private String labeledUserContent(
            ChatRoom chatRoom,
            User user,
            String content,
            ParticipationPauseService.ResumeHint resumeHint
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
        long people = participantRepository.countByChatRoom(chatRoom);

        String labeled = "[PARTICIPANTS: " + roster + "]\n"
                + "[" + role + " | " + user.visibleName() + "]\n"
                + content;
        String sessionNote = "";
        if (speakerConsented) {
            sessionNote = "[SYSTEM | SESSION]\n"
                    + "CONSENT_RECORDED for " + role + ".\n"
                    + "موافقت این نفر قبلاً با دکمهٔ اپلیکیشن ثبت شده است. "
                    + "دیگر از او موافقت، پذیرش قوانین، یا چارچوب مشاوره نخواه. "
                    + "متن قوانین را تکرار نکن.\n"
                    + (people < 2
                    ? "نفر دوم هنوز در گفتگو نیست. از او هم موافقت نخواه. فقط به همین پیام جواب بده.\n"
                    : "فقط به همین پیام جواب بده.\n");
        }
        if (resumeHint != null && resumeHint.handshake()) {
            sessionNote = sessionNote
                    + "[SYSTEM | SESSION]\nREENTRY_COMMITTED for " + role + "\nPAUSE_EXPIRED for " + role + "\n";
        }
        return sessionNote + labeled;
    }


    private String repairConsentReplay(String conversationId, String userText) {
        String fallback = "موافقتت قبلاً ثبت شده و لازم نیست دوباره تأیید کنی. حرفی که زدی را شنیدم؛ هر وقت خواستی ادامه بده.";
        if (conversationId == null || conversationId.isBlank()) {
            return fallback;
        }
        String prompt = "[SYSTEM | SESSION]\n"
                + "CONSENT_ALREADY_RECORDED\n"
                + "جواب قبلی رد شد چون دوباره موافقت می‌خواست.\n"
                + "به پیام کاربر در یک تا سه جملهٔ فارسی جواب بده.\n"
                + "کلمات موافقت، قوانین و چارچوب را به کار نبر و از نفر غایب تأیید نخواه.\n"
                + "USER:\n"
                + (userText == null ? "" : userText);
        try {
            String raw = metisService.sendMessageAllowBlank(conversationId, prompt);
            String visible = new GovernancePacketParser().strip(raw == null ? "" : raw);
            if (visible != null && !visible.isBlank() && !ConsentReplay.asksAgain(visible)) {
                return visible.trim();
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }


    private void sendCalmDownFollowUp(
            ChatRoom chatRoom,
            String conversationId,
            Long agentId,
            ParticipationPauseService.ApplyResult applied
    ) {
        if (chatRoom == null || conversationId == null || conversationId.isBlank() || applied == null) {
            return;
        }
        String who = applied.bothPaused()
                ? "هر دو نفر الان نمی‌توانند پیام بفرستند."
                : "حداقل یک نفر الان نمی‌تواند پیام بفرستد.";
        String prompt = "[SYSTEM | SESSION]\n"
                + "GOVERNANCE_ENFORCED\n"
                + "GOV=" + applied.action() + "\n"
                + "CALM_DOWN\n"
                + who + "\n"
                + "ACTION باید INTERVENE باشد. GOV=NONE و TARGET=NONE و DURATION=0.\n"
                + "دو تا چهار جمله فارسی آرام بگو تا بدن و هیجان پایین بیاید. سرزنش نکن. "
                + "اگر هر دو محدودند روی نفس، مکث، و امنیت جلسه تمرکز کن. "
                + "بگو بعد از آرام شدن، برای برگشتن باید تعهد احترام به جلسه را بپذیرند.";
        try {
            String raw = metisService.sendMessageAllowBlank(conversationId, prompt);
            String visible = new GovernancePacketParser().strip(raw == null ? "" : raw);
            if (visible != null && !visible.isBlank()) {
                chatRealtimeService.saveAgentAndPublish(
                        chatRoom.getId(),
                        agentId,
                        visible
                );
            }
        } catch (Exception exception) {
            String fallback = applied.bothPaused()
                    ? "هر دو نفرتان الان پیام نمی‌فرستید تا فضا آرام شود. چند نفس عمیق بکشید. وقتی آماده شدید، برای برگشتن به گفتگو یک تعهد ساده از شما می‌خواهم: احترام به جلسه و پرهیز از حمله شخصی."
                    : "این مکث برای آرام شدن است، نه تنبیه. چند نفس بکش. وقتی آماده شدی، برای برگشتن به گفتگو ازت می‌خواهم به فرایند جلسه احترام بگذاری.";
            chatRealtimeService.saveAgentAndPublish(
                    chatRoom.getId(),
                    agentId,
                    fallback
            );
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
