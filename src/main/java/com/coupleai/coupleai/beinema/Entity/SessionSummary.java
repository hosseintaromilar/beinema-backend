package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The private brief saved when a session ends. The next session starts from it,
 * so the counselor continues the thread without re-reading the whole history.
 */
@Entity
@Table(name = "session_summaries")
@Getter
@Setter
@NoArgsConstructor
public class SessionSummary extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;
}
