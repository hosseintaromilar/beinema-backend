package com.coupleai.coupleai.beinema.ai;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A running session that stood still while the AI service was down; its clock did not count it. */
@Entity
@Table(name = "ai_session_pauses", indexes = @Index(name = "idx_ai_pause_room", columnList = "chat_room_id,session_number"))
@Getter
@Setter
@NoArgsConstructor
public class AiSessionPause extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    @Column(name = "paused_at", nullable = false)
    private Instant pausedAt;

    @Column(name = "resumed_at", nullable = false)
    private Instant resumedAt;

    @Column(name = "reason_code", length = 20, nullable = false)
    private String reasonCode;
}
