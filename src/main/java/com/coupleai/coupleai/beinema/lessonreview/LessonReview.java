package com.coupleai.coupleai.beinema.lessonreview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The review of one lesson of the library, done in the admin panel with a psychologist: its status,
 * a note (what to change and why), and the edited text when it was changed. In the test phase the
 * status does not stop a lesson from being used; an edit takes the place of the file's text at once.
 */
@Entity
@Table(name = "lesson_reviews")
@Getter
@Setter
@NoArgsConstructor
public class LessonReview {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String NEEDS_REVISION = "NEEDS_REVISION";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", length = 80, nullable = false, unique = true)
    private String lessonId;

    @Column(length = 20, nullable = false)
    private String status = PENDING;

    @Column(columnDefinition = "TEXT")
    private String note;

    /** The whole lesson as JSON, as it is used now; null while the file's text is used. */
    @Column(name = "edited_json", columnDefinition = "MEDIUMTEXT")
    private String editedJson;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
