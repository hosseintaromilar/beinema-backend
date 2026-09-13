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
}