package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One stage of a person's own path (WDEP) that they reached: W their want is
 * confirmed, D they looked at what they do now, E they judged it themselves, P
 * they chose a small step. The note is a short Persian line written for that
 * person only; nobody else sees their path.
 */
@Entity
@Table(name = "person_milestones", indexes = @Index(name = "idx_person_milestones_room", columnList = "chat_room_id"))
@Getter
@Setter
@NoArgsConstructor
public class PersonMilestone extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** OWNER or MEMBER, as the director calls the partners. */
    @Column(name = "participant_role", length = 10, nullable = false)
    private String participantRole;

    /** W, D, E or P. */
    @Column(name = "stage", length = 1, nullable = false)
    private String stage;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    @Column(name = "note", length = 600)
    private String note;

    @Column(name = "decision_id")
    private Long decisionId;
}
