package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.SessionEndReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One counseling session of a room: when the first partner entered it, when its
 * 45 minutes began, when and why it ended, and whether it was completed (something
 * was said in it). The limit of sessions per 24 hours is counted from these.
 */
@Entity
@Table(name = "session_records", indexes = @Index(name = "idx_session_records_room", columnList = "chat_room_id"))
@Getter
@Setter
@NoArgsConstructor
public class SessionRecord extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    /** The first partner entered the session (its 5-minute wait began). */
    @Column(name = "opened_at")
    private Instant openedAt;

    /** The 45 minutes began. */
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private SessionEndReason endReason;

    @Column(name = "completed")
    private Boolean completed;

    /** 1 for the room's first completed session, 2 for the second, and so on. */
    @Column(name = "completed_ordinal")
    private Integer completedOrdinal;

    /** How the creator paid for it: FREE, PAID or UNPAID (the wallet ran short); null until it is settled. */
    @Column(name = "billing", length = 8)
    private String billing;
}
