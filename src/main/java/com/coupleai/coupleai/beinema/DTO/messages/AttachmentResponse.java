package com.coupleai.coupleai.beinema.DTO.messages;

import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AttachmentResponse {

    private Long id;
    private String name;
    private String contentType;
    private Long sizeBytes;
    /** IMAGE or FILE. */
    private String kind;

    public static AttachmentResponse from(MessageAttachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .name(attachment.getOriginalName())
                .contentType(attachment.getContentType())
                .sizeBytes(attachment.getSizeBytes())
                .kind(attachment.getKind())
                .build();
    }
}
