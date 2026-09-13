package com.coupleai.coupleai.beinema.DTO.metis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetisStreamMessage {

    private String type;

    private String content;

    private Object attachments;

    private Object input;

    private Object action;
}

