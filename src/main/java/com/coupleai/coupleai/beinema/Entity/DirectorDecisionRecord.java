package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.DecisionKind;
import com.coupleai.coupleai.beinema.Enum.DecisionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One counselor turn: what the director received and decided, what the
 * platform accepted or corrected, what the voice said, and later what effect
 * the move had. This is the trace that explains why the counselor did what it did.
 */
@Entity
@Table(
        name = "director_decisions",
        indexes = @Index(name = "idx_decision_room", columnList = "chat_room_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_decision_trigger", columnNames = {"chat_room_id", "trigger_message_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class DirectorDecisionRecord extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    /** The newest partner message of the turn; null for events. */
    @Column(name = "trigger_message_id")
    private Long triggerMessageId;

    /** Comma separated ids of all partner messages decided about in this turn. */
    @Column(name = "new_message_ids", length = 255)
    private String newMessageIds;

    @Enumerated(EnumType.STRING)
    @Column(length = 8, nullable = false)
    private DecisionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private DecisionStatus status;

    @Column(length = 40)
    private String action;

    @Column(length = 10)
    private String addressee;

    @Column(name = "session_number")
    private Integer sessionNumber;

    @Column(name = "packet_chars")
    private Integer packetChars;

    @Column(name = "raw_output", columnDefinition = "MEDIUMTEXT")
    private String rawOutput;

    /** The decision as accepted after validation (JSON). */
    @Column(name = "accepted_json", columnDefinition = "MEDIUMTEXT")
    private String acceptedJson;

    @Column(name = "validation_notes", columnDefinition = "TEXT")
    private String validationNotes;

    @Column(name = "rules_used", length = 500)
    private String rulesUsed;

    @Column(name = "retrieved_cards", length = 500)
    private String retrievedCards;

    @Column(name = "safety_level", length = 8)
    private String safetyLevel;

    @Column(name = "voice_output", columnDefinition = "TEXT")
    private String voiceOutput;

    /** Filled at the next turn from the director's reading of the new messages. */
    @Column(name = "outcome_effect", length = 12)
    private String outcomeEffect;

    @Column(name = "outcome_note", length = 500)
    private String outcomeNote;

    @Column(name = "director_ms")
    private Long directorMs;

    @Column(name = "voice_ms")
    private Long voiceMs;

    @Column(length = 1000)
    private String error;
}
