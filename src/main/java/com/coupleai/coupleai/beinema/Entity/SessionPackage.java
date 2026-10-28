package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A bundle of sessions someone bought from their wallet, used one by one until it runs out or expires. */
@Entity
@Table(name = "session_package", indexes = @Index(name = "idx_session_package_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class SessionPackage extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** BRONZE, SILVER or GOLD (SessionPackageTier). */
    @Column(name = "tier", nullable = false, length = 16)
    private String tier;

    @Column(name = "sessions_total", nullable = false)
    private Integer sessionsTotal;

    @Column(name = "sessions_used", nullable = false)
    private Integer sessionsUsed = 0;

    /** What was paid, in toman. */
    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public int sessionsLeft() {
        return Math.max(0, sessionsTotal - (sessionsUsed == null ? 0 : sessionsUsed));
    }
}
