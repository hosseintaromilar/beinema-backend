package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.IntentEpistemic;
import com.coupleai.coupleai.beinema.Enum.IntentKind;
import com.coupleai.coupleai.beinema.Enum.IntentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One want, meaning, concern or goal of one partner, with how we know it and
 * where it stands. A change never overwrites a row: a new row points to its
 * parent and the parent becomes REFINED or SUPERSEDED.
 */
@Entity
@Table(name = "client_intents", indexes = @Index(name = "idx_intent_room", columnList = "chat_room_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClientIntent extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "participant_user_id", nullable = false)
    private Long participantUserId;

    /** OWNER or MEMBER, as the director names the partners. */
    @Column(name = "participant_role", length = 10, nullable = false)
    private String participantRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private IntentKind kind;

    /** The partner's own words, exactly as written. */
    @Column(columnDefinition = "TEXT")
    private String quote;

    @Column(length = 600)
    private String interpretation;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private IntentEpistemic epistemic;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private IntentStatus status;

    private Double confidence;

    @Column(length = 8)
    private String importance;

    /** Comma separated message ids. */
    @Column(name = "evidence_message_ids", length = 255)
    private String evidenceMessageIds;

    /** Comma separated intent ids of competing readings. */
    @Column(name = "alternative_ids", length = 255)
    private String alternativeIds;

    @Column(name = "parent_id")
    private Long parentId;

    /** ELABORATION, PRIORITY_SHIFT or TRUE_CHANGE, relative to the parent. */
    @Column(name = "change_kind", length = 16)
    private String changeKind;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    /** What changed the status last: d12 (a decision) or q7 (a question). */
    @Column(name = "changed_by", length = 24)
    private String changedBy;

    @Column(name = "session_number")
    private Integer sessionNumber;

    @Column(name = "created_decision_id")
    private Long createdDecisionId;
}
