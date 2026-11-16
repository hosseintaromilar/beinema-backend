package com.coupleai.coupleai.beinema.mirror;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One person and one mirror: when they opened it in the app, and when it was emailed to them because
 * they had not opened it within a day. A mirror written again after a later session counts as new:
 * both times are compared with the mirror's builtAt.
 */
@Entity
@Table(name = "mirror_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_mirror_read", columnNames = {"report_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MirrorRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "seen_at")
    private Instant seenAt;

    @Column(name = "emailed_at")
    private Instant emailedAt;

    /** Opened in the app, or emailed, since this version of the mirror was written. */
    boolean handled(Instant builtAt) {
        return builtAt != null && ((seenAt != null && !seenAt.isBefore(builtAt))
                || (emailedAt != null && !emailedAt.isBefore(builtAt)));
    }

    boolean seen(Instant builtAt) {
        return builtAt != null && seenAt != null && !seenAt.isBefore(builtAt);
    }
}
