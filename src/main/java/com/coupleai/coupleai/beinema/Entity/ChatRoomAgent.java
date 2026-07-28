package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.BitSet;
@Entity
@Table(
        name = "chat_room_agents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_room_agent",
                        columnNames = {
                                "chat_room_id",
                                "agent_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomAgent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "chat_room_id",
            nullable = false
    )
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "agent_id",
            nullable = false
    )
    private Agent agent;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();

    @Column(nullable = false)
    private Long addedBy;
}