package com.coupleai.coupleai.beinema.DTO.Feedback;

/** One survey answer as the admin panel lists it. */
public record FeedbackView(
        Long id,
        Long chatRoomId,
        String roomTitle,
        String userName,
        String role,
        Integer heard,
        Integer fairness,
        Integer progress,
        Integer ease,
        Integer recommend,
        String comment,
        Long createdAt
) {
}
