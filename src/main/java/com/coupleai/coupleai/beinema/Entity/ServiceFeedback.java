package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The short survey offered when someone ends a conversation for good: five
 * multiple-choice questions (each answer 1 to 5; null when skipped) and an
 * optional comment. One per person and room; a second answer replaces the first.
 */
@Entity
@Table(name = "service_feedback", indexes = @Index(name = "idx_service_feedback_room_user", columnList = "chat_room_id,user_id"))
@Getter
@Setter
@NoArgsConstructor
public class ServiceFeedback extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** OWNER or MEMBER when they answered. */
    @Column(name = "role", length = 16)
    private String role;

    /** How heard they felt. */
    @Column(name = "heard")
    private Integer heard;

    /** How fair (not taking sides) the counselor was. */
    @Column(name = "fairness")
    private Integer fairness;

    /** How much the sessions reached something concrete. */
    @Column(name = "progress")
    private Integer progress;

    /** How easy the app was to use. */
    @Column(name = "ease")
    private Integer ease;

    /** How likely they recommend Beinema. */
    @Column(name = "recommend")
    private Integer recommend;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
}
