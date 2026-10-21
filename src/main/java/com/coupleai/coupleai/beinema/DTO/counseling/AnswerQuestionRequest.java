package com.coupleai.coupleai.beinema.DTO.counseling;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A partner's answer to a structured question. clientRequestId makes a repeated submit harmless. */
@Getter
@Setter
@NoArgsConstructor
public class AnswerQuestionRequest {

    private String optionId;

    private String freeText;

    private String clientRequestId;
}
