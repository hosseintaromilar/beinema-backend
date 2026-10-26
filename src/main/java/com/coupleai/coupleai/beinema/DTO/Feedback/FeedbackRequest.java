package com.coupleai.coupleai.beinema.DTO.Feedback;

import lombok.Getter;
import lombok.Setter;

/** Answers 1 to 5 (null = skipped) and an optional comment. */
@Getter
@Setter
public class FeedbackRequest {

    private Integer heard;
    private Integer fairness;
    private Integer progress;
    private Integer ease;
    private Integer recommend;
    private String comment;
}
