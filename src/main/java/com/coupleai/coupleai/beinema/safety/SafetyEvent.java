package com.coupleai.coupleai.beinema.safety;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A safety card was shown: to whom, in which room and session, for which kind of danger. Never the words. */
@Entity
@Table(name = "safety_events", indexes = @Index(name = "idx_safety_event_room", columnList = "chat_room_id,user_id"))
@Getter
@Setter
@NoArgsConstructor
public class SafetyEvent extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    /** Who was shown the card. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 20, nullable = false)
    private String category;

    @Column(name = "session_number")
    private Integer sessionNumber;
}
