package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
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
import com.coupleai.coupleai.beinema.governance.CounselorSignal;
import com.coupleai.coupleai.beinema.governance.ReplySegments;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRealtimeService {

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final UserRepository userRepository;
    private final ChatRoomEventHub chatRoomEventHub;
    private final com.coupleai.coupleai.beinema.Repository.MessageAttachmentRepository attachmentRepository;
    private final com.coupleai.coupleai.beinema.Repository.ClarifyingQuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<MessageResponse> toResponses(ChatRoom chatRoom, List<Message> messages) {
        Map<Long, ChatRoomParticipant> participants = participantsByUserId(chatRoom);
        String agentName = resolveAgentName(chatRoom.getId());
        java.util.Map<Long, List<com.coupleai.coupleai.beinema.DTO.messages.AttachmentResponse>> files =
                attachmentRepository.findAllByChatRoomIdAndMessageIdIsNotNull(chatRoom.getId()).stream()
                        .collect(Collectors.groupingBy(
                                com.coupleai.coupleai.beinema.Entity.MessageAttachment::getMessageId,
                                Collectors.mapping(
                                        com.coupleai.coupleai.beinema.DTO.messages.AttachmentResponse::from,
                                        Collectors.toList()
                                )
                        ));
        java.util.Set<String> questionIds = new java.util.HashSet<>();
        for (Message message : messages) {
            String publicId = com.coupleai.coupleai.beinema.governance.QuestionMarkers.questionId(message.getContent());
            if (publicId != null) {
                questionIds.add(publicId);
            }
        }
        Map<String, com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion> questions = questionIds.isEmpty()
                ? Map.of()
                : questionRepository.findAllByPublicIdIn(questionIds).stream()
                .collect(Collectors.toMap(
                        com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion::getPublicId,
                        question -> question,
                        (first, second) -> first
                ));
        return messages.stream()
                .map(message -> {
                    MessageResponse response = toResponse(message, participants, agentName);
                    response.setAttachments(files.getOrDefault(message.getId(), List.of()));
                    attachQuestion(response, message, questions, participants);
                    return response;
                })
                .toList();
    }

    /** The question a counselor message shows, as the chat displays it (addressee, options, status). */
    private void attachQuestion(
            MessageResponse response,
            Message message,
            Map<String, com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion> questions,
            Map<Long, ChatRoomParticipant> participants
    ) {
        String publicId = com.coupleai.coupleai.beinema.governance.QuestionMarkers.questionId(message.getContent());
        if (publicId == null) {
            return;
        }
        com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion question = questions.get(publicId);
        if (question == null) {
            return;
        }
        ChatRoomParticipant addressee = participants.get(question.getParticipantUserId());
        String name = addressee == null || addressee.getUser() == null ? null : addressee.getUser().visibleName();
        response.setQuestion(com.coupleai.coupleai.beinema.counseling.QuestionViews.view(question, name));
    }

    @Transactional
    public void publish(Long chatRoomId, Message message) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
        if (chatRoom == null || message == null) {
            return;
        }
        chatRoomEventHub.publish(chatRoomId, toResponse(chatRoom, message));
    }

    @Transactional
    public Message saveAgentAndPublish(Long chatRoomId, Long agentId, String content) {
        return saveAgentAndPublish(chatRoomId, agentId, content, null);
    }

    @Transactional
    public Message saveAgentAndPublish(Long chatRoomId, Long agentId, String content, Long replyToMessageId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        // A reply that still carries BREAK markers is joined into one readable message.
        String cleaned = ReplySegments.plain(content == null ? "" : content).trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        Message previous = messageRepository
                .findFirstByChatRoomAndDeletedFalseOrderBySequenceNumberDesc(chatRoom)
                .orElse(null);
        if (previous != null
                && previous.getSenderType() == MessageSenderType.AGENT
                && cleaned.equals(previous.getContent() == null ? "" : previous.getContent().trim())) {
            chatRoomEventHub.publish(chatRoomId, toResponse(chatRoom, previous));
            return previous;
        }

        int nextSequence = messageRepository
                .findMaxSequenceNumberByChatRoom(chatRoom)
                .orElse(0)
                + 1;

        Message aiMessage = Message.builder()
                .chatRoom(chatRoom)
                .senderType(MessageSenderType.AGENT)
                .senderId(agentId == null ? 0L : agentId)
                .content(cleaned)
                .status(MessageStatus.SENT)
                .sequenceNumber(nextSequence)
                .replyToMessageId(replyToMessageId)
                .build();

        messageRepository.saveAndFlush(aiMessage);
        chatRoomEventHub.publish(chatRoomId, toResponse(chatRoom, aiMessage));
        return aiMessage;
    }

    /**
     * Tells every open client that the counselor is typing the next part of a
     * multi-message reply. Purely a presentation hint; it carries no decision.
     */
    public void publishAgentTyping(Long chatRoomId, long durationMillis) {
        publishAgentTyping(chatRoomId, durationMillis, "writing");
    }

    /**
     * The same hint with the stage of the counselor's turn, so the page can say
     * what is really happening: "thinking" (deciding what to do), "writing"
     * (the reply is being written) or "continuing" (the next part of a reply).
     */
    public void publishAgentTyping(Long chatRoomId, long durationMillis, String phase) {
        chatRoomEventHub.publishNamed(
                chatRoomId,
                "agent_typing",
                java.util.Map.of("durationMs", Math.max(500L, durationMillis), "phase", phase == null ? "writing" : phase)
        );
    }

    /**
     * Puts a small emoji under a partner's message: the counselor's silent
     * answer. The brain picks the meaning; this only stores and announces it.
     */
    @Transactional
    public void reactAndPublish(Long chatRoomId, Long messageId, String token) {
        if (chatRoomId == null || messageId == null || token == null || token.isBlank()) {
            return;
        }
        Message target = messageRepository.findById(messageId).orElse(null);
        if (target == null
                || target.getChatRoom() == null
                || !chatRoomId.equals(target.getChatRoom().getId())
                || target.getSenderType() != MessageSenderType.USER) {
            return;
        }
        target.setAgentReaction(token);
        messageRepository.saveAndFlush(target);
        chatRoomEventHub.publishNamed(
                chatRoomId,
                "message_reaction",
                java.util.Map.of(
                        "messageId", messageId,
                        "token", token,
                        "emoji", String.valueOf(CounselorSignal.emojiFor(token))
                )
        );
    }

    /** Sends a counselor sticker as a reply to a partner's message. */
    @Transactional
    public Message saveStickerAndPublish(Long chatRoomId, Long agentId, String token, Long replyToMessageId) {
        String normalized = CounselorSignal.normalize(token);
        if (normalized == null) {
            return null;
        }
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        int nextSequence = messageRepository
                .findMaxSequenceNumberByChatRoom(chatRoom)
                .orElse(0)
                + 1;
        Message sticker = Message.builder()
                .chatRoom(chatRoom)
                .senderType(MessageSenderType.AGENT)
                .senderId(agentId == null ? 0L : agentId)
                .content(CounselorSignal.stickerContent(normalized))
                .status(MessageStatus.SENT)
                .sequenceNumber(nextSequence)
                .replyToMessageId(replyToMessageId)
                .build();
        messageRepository.saveAndFlush(sticker);
        chatRoomEventHub.publish(chatRoomId, toResponse(chatRoom, sticker));
        return sticker;
    }

    /** A platform notice that only one member of the room sees, now and in the history. */
    @Transactional
    public Message savePrivateSystemAndPublish(Long chatRoomId, Long userId, String content) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        int nextSequence = messageRepository
                .findMaxSequenceNumberByChatRoom(chatRoom)
                .orElse(0)
                + 1;
        Message notice = Message.builder()
                .chatRoom(chatRoom)
                .senderType(MessageSenderType.SYSTEM)
                .senderId(0L)
                .content(content == null ? "" : content)
                .status(MessageStatus.SENT)
                .sequenceNumber(nextSequence)
                .visibleToUserId(userId)
                .build();
        messageRepository.saveAndFlush(notice);
        chatRoomEventHub.publishToUser(chatRoomId, userId, toResponse(chatRoom, notice));
        return notice;
    }

    @Transactional
    public Message saveSystemAndPublish(Long chatRoomId, String content) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        int nextSequence = messageRepository
                .findMaxSequenceNumberByChatRoom(chatRoom)
                .orElse(0)
                + 1;

        Message systemMessage = Message.builder()
                .chatRoom(chatRoom)
                .senderType(MessageSenderType.SYSTEM)
                .senderId(0L)
                .content(content == null ? "" : content)
                .status(MessageStatus.SENT)
                .sequenceNumber(nextSequence)
                .build();

        messageRepository.saveAndFlush(systemMessage);
        chatRoomEventHub.publish(chatRoomId, toResponse(chatRoom, systemMessage));
        return systemMessage;
    }

    private MessageResponse toResponse(ChatRoom chatRoom, Message message) {
        Map<Long, ChatRoomParticipant> participants = participantsByUserId(chatRoom);
        MessageResponse response = toResponse(
                message,
                participants,
                resolveAgentName(chatRoom.getId())
        );
        String questionId = com.coupleai.coupleai.beinema.governance.QuestionMarkers.questionId(message.getContent());
        if (questionId != null) {
            questionRepository.findByPublicId(questionId).ifPresent(question ->
                    attachQuestion(response, message, Map.of(questionId, question), participants));
        }
        if (message.getId() != null) {
            response.setAttachments(
                    attachmentRepository.findAllByMessageId(message.getId()).stream()
                            .map(com.coupleai.coupleai.beinema.DTO.messages.AttachmentResponse::from)
                            .toList()
            );
        }
        return response;
    }

    private Map<Long, ChatRoomParticipant> participantsByUserId(ChatRoom chatRoom) {
        return participantRepository.findAllByChatRoom(chatRoom)
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getUser().getId(),
                        item -> item,
                        (first, second) -> first
                ));
    }

    private String resolveAgentName(Long chatRoomId) {
        return chatRoomAgentRepository.findActiveAgentNames(chatRoomId)
                .stream()
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("مشاور");
    }

    private MessageResponse toResponse(
            Message message,
            Map<Long, ChatRoomParticipant> participantsByUserId,
            String agentName
    ) {
        MessageResponse response = MessageResponse.from(message);
        if (message.getReplyToMessageId() != null) {
            messageRepository.findById(message.getReplyToMessageId()).ifPresent(original -> {
                String text = CounselorSignal.stripSticker(original.getContent());
                response.setReplyExcerpt(text.length() > 70 ? text.substring(0, 70) + "..." : text);
                if (original.getSenderType() == MessageSenderType.AGENT) {
                    response.setReplyToSender(
                            agentName == null || agentName.isBlank() ? "مشاور" : agentName
                    );
                } else {
                    ChatRoomParticipant quoted = participantsByUserId.get(original.getSenderId());
                    if (quoted != null && quoted.getUser() != null) {
                        response.setReplyToSender(quoted.getUser().visibleName());
                    }
                }
            });
        }

        if (message.getSenderType() == MessageSenderType.AGENT) {
            String name = agentName == null || agentName.isBlank() ? "مشاور" : agentName;
            response.setDisplayName(name);
            response.setSenderName(name);
            return response;
        }

        if (message.getSenderType() != MessageSenderType.USER) {
            return response;
        }

        ChatRoomParticipant participant = participantsByUserId.get(message.getSenderId());
        User sender = participant != null ? participant.getUser() : null;

        if (sender == null && message.getSenderId() != null) {
            sender = userRepository.findById(message.getSenderId()).orElse(null);
        }

        if (sender == null) {
            return response;
        }

        if (participant != null) {
            response.setParticipantId(participant.getId());
            response.setParticipantRole(toStableRole(participant.getRole()));
        }

        response.setDisplayName(sender.visibleName());
        response.setSenderName(sender.visibleName());
        return response;
    }

    private String toStableRole(ParticipantRole role) {
        if (role == ParticipantRole.OWNER || role == ParticipantRole.PARTNER_A) {
            return "OWNER";
        }
        if (role == ParticipantRole.MEMBER || role == ParticipantRole.PARTNER_B) {
            return "MEMBER";
        }
        return role == null ? "MEMBER" : role.name();
    }
}
