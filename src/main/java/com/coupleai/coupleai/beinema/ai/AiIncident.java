package com.coupleai.coupleai.beinema.ai;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One stretch of time the AI service was down, for the admin. No words of anyone are kept. */
@Entity
@Table(name = "ai_incidents")
@Getter
@Setter
@NoArgsConstructor
public class AiIncident extends BaseEntity {

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "error_code", length = 20, nullable = false)
    private String errorCode;

    /** Sessions that were running when it started; their clock was moved on by the outage. */
    @Column(name = "affected_sessions")
    private Integer affectedSessions;

    @Column(length = 300)
    private String notes;
}
