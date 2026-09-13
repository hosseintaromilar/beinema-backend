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
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
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

    private final CurrentUserService currentUserService;

    private final MetisService metisService;

    private final ObjectMapper objectMapper;


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

        java.util.Map<Long, ChatRoomParticipant> participantsByUserId =
                participantRepository.findAllByChatRoom(chatRoom)
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(
                                item -> item.getUser().getId(),
                                item -> item,
                                (first, second) -> first
                        ));

        return messageRepository
                .findAllByChatRoomOrderBySequenceNumberAsc(
                        chatRoom
                )
                .stream()
                .map(message -> toMessageResponse(message, participantsByUserId))
                .toList();
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


        /*
         * 4. Make sure Metis conversation exists.
         */

        if (
                chatRoom.getAiConversationId() == null
                        ||
                        chatRoom.getAiConversationId().isBlank()
        ) {

            throw new RuntimeException(
                    "AI conversation has not been initialized"
            );
        }


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


        messageRepository.save(userMessage);

        String conversationId = chatRoom.getAiConversationId();
        Long chatRoomPk = chatRoom.getId();
        String userContent = labeledUserContent(chatRoom, user, request.getContent());
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


            try {

                metisService.streamMessage(

                        conversationId,

                        userContent,

                        chunk -> {

                            /*
                             * Ignore null/empty chunks.
                             */

                            if (
                                    chunk == null
                                            ||
                                            chunk.isEmpty()
                            ) {

                                return;
                            }


                            /*
                             * ALWAYS preserve the exact chunk.
                             */

                            aiResponse.append(chunk);


                            /*
                             * If browser disconnected,
                             * don't try to send anymore.
                             */

                            if (
                                    emitterClosed.get()
                            ) {

                                return;
                            }


                            try {

                                /*
                                 * Send EXACTLY the chunk
                                 * received from Metis.
                                 */

                                emitter.send(
                                        SseEmitter.event()
                                                .name("message")
                                                .data(
                                                        objectMapper.writeValueAsString(chunk),
                                                        MediaType.TEXT_PLAIN
                                                )
                                );

                            } catch (
                                    IOException
                                    |
                                    IllegalStateException e
                            ) {

                                /*
                                 * SSE connection is no longer usable.
                                 */

                                emitterClosed.set(true);

                            }
                        }
                );


                /*
                 * 9. Save complete AI response.
                 */

                int aiSequence =
                        messageRepository
                                .findMaxSequenceNumberByChatRoom(chatRoom)
                                .orElse(nextSequence)
                                + 1;


                Message aiMessage =
                        Message.builder()
                                .chatRoom(chatRoom)
                                .senderType(
                                        MessageSenderType.AGENT
                                )
                                .senderId(agentId)
                                .content(
                                        aiResponse.toString()
                                )
                                .status(
                                        MessageStatus.SENT
                                )
                                .sequenceNumber(
                                        aiSequence
                                )
                                .build();


                messageRepository.save(aiMessage);


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
                .findAllByChatRoom_Id(chatRoomId)
                .stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .map(item -> item.getAgent().getId())
                .findFirst()
                .orElse(0L);
    }


    private String labeledUserContent(
            ChatRoom chatRoom,
            User user,
            String content
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
                        + item.getUser().getName())
                .reduce((left, right) -> left + "; " + right)
                .orElse(role + "=" + user.getName());

        return "[PARTICIPANTS: " + roster + "]\n"
                + "[" + role + " | " + user.getName() + "]\n"
                + content;
    }


    private String toStableRole(ParticipantRole role) {

        if (role == ParticipantRole.OWNER || role == ParticipantRole.PARTNER_A) {
            return "OWNER";
        }

        if (role == ParticipantRole.MEMBER || role == ParticipantRole.PARTNER_B) {
            return "MEMBER";
        }

        return role.name();
    }


    private MessageResponse toMessageResponse(
            Message message,
            java.util.Map<Long, ChatRoomParticipant> participantsByUserId
    ) {

        MessageResponse response = MessageResponse.from(message);

        if (message.getSenderType() != MessageSenderType.USER) {
            return response;
        }

        ChatRoomParticipant participant =
                participantsByUserId.get(message.getSenderId());

        if (participant == null) {
            return response;
        }

        response.setParticipantId(participant.getId());
        response.setParticipantRole(toStableRole(participant.getRole()));
        response.setDisplayName(participant.getUser().getName());

        return response;
    }
}
