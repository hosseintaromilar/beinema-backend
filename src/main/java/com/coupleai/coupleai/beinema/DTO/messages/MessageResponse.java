package com.coupleai.coupleai.beinema.DTO.messages;

import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class MessageResponse {

    private Long id;

    private MessageSenderType senderType;

    private Long senderId;

    private Long participantId;

    private String participantRole;

    private String displayName;

    private String senderName;

    private String senderAvatar;

    private String content;

    private MessageStatus status;

    private Integer sequenceNumber;

    private LocalDateTime createdAt;


    /**
     * Convert Message entity to MessageResponse DTO.
     */
    public static MessageResponse from(
            Message message
    ) {

        if (message == null) {
            return null;
        }


        return MessageResponse.builder()

                .id(
                        message.getId()
                )

                .senderType(
                        message.getSenderType()
                )

                .senderId(
                        message.getSenderId()
                )

                .displayName(
                        message.getSenderType() == MessageSenderType.AGENT
                                ? "دستیار"
                                : null
                )

                .content(
                        message.getContent()
                )

                .status(
                        message.getStatus()
                )

                .sequenceNumber(
                        message.getSequenceNumber()
                )

                .createdAt(
                        message.getCreatedAt()
                )

                .build();
    }
}