package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One person's lock on one conversation: a PIN of their own, so whoever picks up their phone while they
 * are signed in cannot open it. Each partner locks their own entry; the other partner is not affected.
 * Kept apart from the participant row, which many services save, so a stale save never drops a lock.
 */
@Entity
@Table(name = "room_locks", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "chat_room_id"}))
@Getter
@Setter
public class RoomLock extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    /** BCrypt of the PIN; never the PIN itself. */
    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    /** They forgot the PIN and were signed out: a sign-in after this moment may remove the lock. */
    @Column(name = "reset_requested_at")
    private Instant resetRequestedAt;
}
