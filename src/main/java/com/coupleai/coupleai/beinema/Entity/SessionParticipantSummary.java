package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What one person brought to one session, in their own words: wants, worries,
 * the behaviour they described and the step they accepted. Saved when the
 * session ends. Private to that person and to the counselor.
 */
@Entity
@Table(
        name = "session_participant_summaries",
        indexes = {
                @Index(name = "idx_sps_room_session", columnList = "chat_room_id, session_number"),
                @Index(name = "idx_sps_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SessionParticipantSummary extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;
}
