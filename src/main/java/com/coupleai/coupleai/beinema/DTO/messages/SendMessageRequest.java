package com.coupleai.coupleai.beinema.DTO.messages;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    @Size(max = 10000, message = "متن پیام بیش از حد طولانی است.")
    private String content;

    /** Optional: the message this one replies to (must belong to the same room). */
    private Long replyToMessageId;

    /** Ids of files uploaded earlier with POST /files; sent together with this message. */
    private java.util.List<Long> attachmentIds;

}