package com.coupleai.coupleai.beinema.DTO.messages;

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

    private String senderName;

    private String senderAvatar;

    private String content;

    private MessageStatus status;

    private Integer sequenceNumber;

    private LocalDateTime createdAt;

}