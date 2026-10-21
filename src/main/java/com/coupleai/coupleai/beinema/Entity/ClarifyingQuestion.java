package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.QuestionStatus;
import com.coupleai.coupleai.beinema.Enum.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A structured clarifying question to one partner. This row is the only source
 * of its state; the counselor message that shows it carries just its public id
 * (a [[QUESTION|ID=...]] marker) and the answer message a [[ANSWER|Q=...]] marker.
 */
@Entity
@Table(name = "clarifying_questions", indexes = @Index(name = "idx_question_room", columnList = "chat_room_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClarifyingQuestion extends BaseEntity {

    @Column(name = "public_id", length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    /** The addressee: only this partner can answer. */
    @Column(name = "participant_user_id", nullable = false)
    private Long participantUserId;

    @Column(name = "participant_role", length = 10, nullable = false)
    private String participantRole;

    @Column(name = "decision_id")
    private Long decisionId;

    /** The counselor message that shows the question. */
    @Column(name = "message_id")
    private Long messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 10, nullable = false)
    private QuestionType questionType;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;

    /** JSON array of {id, label, kind, intentId}. */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    /** Comma separated intent ids the question decides between. */
    @Column(name = "target_intent_ids", length = 255)
    private String targetIntentIds;

    @Column(length = 500)
    private String reason;

    @Column(name = "decision_impact", length = 8)
    private String decisionImpact;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private QuestionStatus status;

    @Column(name = "status_reason", length = 300)
    private String statusReason;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "selected_option_id", length = 24)
    private String selectedOptionId;

    @Column(name = "free_text", columnDefinition = "TEXT")
    private String freeText;

    @Column(name = "answer_message_id")
    private Long answerMessageId;

    /** Lets a repeated submit of the same answer return the same result. */
    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;

    /** JSON: which intents were confirmed or rejected by the answer. */
    @Column(name = "resolution_json", columnDefinition = "TEXT")
    private String resolutionJson;

    @Column(name = "session_number")
    private Integer sessionNumber;

    @Version
    private Long version;
}
