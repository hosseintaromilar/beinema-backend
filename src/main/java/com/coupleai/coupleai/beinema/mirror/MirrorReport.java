package com.coupleai.coupleai.beinema.mirror;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The mirror of one conversation for one day, written at 22:00 Tehran time for the sessions that
 * ended since the night before. Shared: both people of the conversation read it, in the app and
 * by email. Made only from what both of them saw in the room.
 */
@Entity
@Table(name = "mirror_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_mirror_room_day", columnNames = {"chat_room_id", "day"}),
        indexes = @Index(name = "idx_mirror_room", columnList = "chat_room_id"))
@Getter
@Setter
@NoArgsConstructor
public class MirrorReport {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    /** Too little was said for a mirror; nothing to send. */
    public static final String SKIPPED = "SKIPPED";
    /** Writing it failed every time it was tried. */
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    /** The Tehran day whose 22:00 closed this report. */
    @Column(nullable = false)
    private LocalDate day;

    @Column(length = 10, nullable = false)
    private String status = PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "sessions_count", nullable = false)
    private int sessionsCount;

    /** starting, softer, same or returned: the tone of the day, for the little chart. */
    @Column(length = 32)
    private String mood;

    @Column(name = "where_we_were", columnDefinition = "TEXT")
    private String whereWeWere;

    @Column(name = "where_we_are", columnDefinition = "TEXT")
    private String whereWeAre;

    @Column(name = "needs_json", columnDefinition = "TEXT")
    private String needsJson;

    @Column(name = "patterns_json", columnDefinition = "TEXT")
    private String patternsJson;

    @Column(name = "next_step", columnDefinition = "TEXT")
    private String nextStep;

    @Column(name = "daily_sentence", columnDefinition = "TEXT")
    private String dailySentence;

    @Column(name = "built_at")
    private Instant builtAt;

    /** How many of the two got it by email. */
    @Column(name = "emailed", nullable = false)
    private int emailed;

    @Column(name = "last_error", length = 300)
    private String lastError;
}
