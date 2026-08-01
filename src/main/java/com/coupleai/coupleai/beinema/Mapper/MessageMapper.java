package com.coupleai.coupleai.beinema.Mapper;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;
import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;

public class MessageMapper {

    public static Message toEntity(
            SendMessageRequest request,
            ChatRoom chatRoom,
            Long senderId,
            MessageSenderType senderType,
            Integer sequenceNumber
    ) {

        return Message.builder()
                .chatRoom(chatRoom)
                .senderId(senderId)
                .senderType(senderType)
                .content(request.getContent())
                .status(MessageStatus.SENT)
                .sequenceNumber(sequenceNumber)
                .build();

    }

    public static MessageResponse toResponse(
            Message message,
            User user,
            Agent agent
    ) {

        return MessageResponse.builder()
                .id(message.getId())
                .senderType(message.getSenderType())
                .senderId(message.getSenderId())
                .senderName(
                        user != null
                                ? user.getName()
                                : agent.getName()
                )
                .senderAvatar(
                        user != null
                                ? "🧑"
                                : agent.getAvatar()
                )
                .content(message.getContent())
                .status(message.getStatus())
                .sequenceNumber(message.getSequenceNumber())
                .createdAt(message.getCreatedAt())
                .build();

    }

}