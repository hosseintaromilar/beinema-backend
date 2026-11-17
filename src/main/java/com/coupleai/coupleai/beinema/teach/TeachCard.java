package com.coupleai.coupleai.beinema.teach;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A lesson card made for one person in a conversation: our lesson, fitted by the teacher to what
 * they told us and to where they stand. Only that person sees it, in the room and in «درس‌های من».
 * The little question at its end checks the lesson was understood; the director reads the answer.
 */
@Entity
@Table(name = "teach_cards", indexes = {
        @Index(name = "idx_teach_room", columnList = "chat_room_id"),
        @Index(name = "idx_teach_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class TeachCard extends BaseEntity {

    @Column(name = "public_id", length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lesson_id", length = 60, nullable = false)
    private String lessonId;

    @Column(length = 200, nullable = false)
    private String title;

    /** KNOW, ACCEPT or DO: where the person stood when it was made. */
    @Column(length = 10)
    private String readiness;

    /** JSON: situation, gap, points, example, try, check {question, options}, source. */
    @Column(name = "content_json", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String contentJson;

    /** The index of the right option of the check; never sent to the browser before an answer. */
    @Column(name = "check_answer", nullable = false)
    private int checkAnswer;

    @Column(name = "chosen")
    private Integer chosen;

    @Column(name = "understood")
    private Boolean understood;

    /** True when the teacher fitted it; false when the lesson's own words were used. */
    @Column(name = "fitted", nullable = false)
    private boolean fitted;

    /** When it went out in the nightly email (with the mirror or on its own); null: not yet. */
    @Column(name = "emailed_at")
    private java.time.Instant emailedAt;

    /** When its unanswered question was recalled once more, the night after; null: not yet. */
    @Column(name = "reminded_at")
    private java.time.Instant remindedAt;

    /** SESSION: a lesson after a session (SessionLessonService); null: a card of the teaching form, inside the talk. */
    @Column(length = 10)
    private String kind;

    /** SESSION: the session it came after. */
    @Column(name = "session_number")
    private Integer sessionNumber;

    /** SESSION: its exercise must be done before the next lesson of this conversation opens. */
    @Column(name = "exercise_required")
    private Boolean exerciseRequired;

    /** SESSION: what the person wrote about doing the exercise; only they see it. */
    @Column(name = "exercise_note", columnDefinition = "TEXT")
    private String exerciseNote;

    @Column(name = "exercise_done_at")
    private java.time.Instant exerciseDoneAt;

    /** When its owner first opened it; null: still new (the conversation card on the dashboard says so). */
    @Column(name = "seen_at")
    private java.time.Instant seenAt;

    public static final String SESSION = "SESSION";

    public boolean afterSession() {
        return SESSION.equals(kind);
    }

    /** A required exercise not written yet. */
    public boolean exerciseOpen() {
        return Boolean.TRUE.equals(exerciseRequired) && exerciseDoneAt == null;
    }
}
