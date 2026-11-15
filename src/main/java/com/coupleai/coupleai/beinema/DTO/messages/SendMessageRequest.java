package com.coupleai.coupleai.beinema.DTO.messages;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    @Size(max = com.coupleai.coupleai.beinema.Util.MessageLimits.MAX_CHARS, message = com.coupleai.coupleai.beinema.Util.MessageLimits.TOO_LONG)
    private String content;

    /** Optional: the message this one replies to (must belong to the same room). */
    private Long replyToMessageId;

    /** Ids of files uploaded earlier with POST /files; sent together with this message. */
    private java.util.List<Long> attachmentIds;

}