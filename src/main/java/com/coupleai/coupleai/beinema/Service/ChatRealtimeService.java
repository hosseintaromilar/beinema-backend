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

    @Transactional(readOnly = true)
    public List<MessageResponse> toResponses(ChatRoom chatRoom, List<Message> messages) {
        Map<Long, ChatRoomParticipant> participants = participantsByUserId(chatRoom);
        String agentName = resolveAgentName(chatRoom.getId());
        return messages.stream()
                .map(message -> toResponse(message, participants, agentName))
                .toList();
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
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        int nextSequence = messageRepository
                .findMaxSequenceNumberByChatRoom(chatRoom)
                .orElse(0)
                + 1;

        Message aiMessage = Message.builder()
                .chatRoom(chatRoom)
                .senderType(MessageSenderType.AGENT)
                .senderId(agentId == null ? 0L : agentId)
                .content(content == null ? "" : content)
                .status(MessageStatus.SENT)
                .sequenceNumber(nextSequence)
                .build();

        messageRepository.saveAndFlush(aiMessage);
        chatRoomEventHub.publish(chatRoomId, toResponse(chatRoom, aiMessage));
        return aiMessage;
    }

    private MessageResponse toResponse(ChatRoom chatRoom, Message message) {
        return toResponse(
                message,
                participantsByUserId(chatRoom),
                resolveAgentName(chatRoom.getId())
        );
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
                .orElse("دستیار");
    }

    private MessageResponse toResponse(
            Message message,
            Map<Long, ChatRoomParticipant> participantsByUserId,
            String agentName
    ) {
        MessageResponse response = MessageResponse.from(message);

        if (message.getSenderType() == MessageSenderType.AGENT) {
            String name = agentName == null || agentName.isBlank() ? "دستیار" : agentName;
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

        response.setDisplayName(sender.getName());
        response.setSenderName(sender.getName());
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
