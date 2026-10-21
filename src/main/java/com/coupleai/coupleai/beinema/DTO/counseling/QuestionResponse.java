package com.coupleai.coupleai.beinema.DTO.counseling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** A structured clarifying question as the chat shows it. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private String id;

    /** CONFIRM or CHOOSE. */
    private String type;

    private String text;

    private List<Option> options;

    private Long addresseeUserId;

    private String addresseeName;

    /** OPEN, ANSWERED, CANCELLED, EXPIRED (DRAFT is never shown). */
    private String status;

    private String selectedOptionId;

    private String freeText;

    private LocalDateTime answeredAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private String id;
        private String label;
        /** HYPOTHESIS, NONE, UNSURE, YES or NO. */
        private String kind;
        /** The partner must explain in their own words. */
        private boolean needsText;
    }
}
