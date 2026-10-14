package com.coupleai.coupleai.beinema.DTO.metis;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetisMessage {

    private String content;

    @Builder.Default
    private String type = "USER";

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    private java.util.List<MetisAttachment> attachments;
}