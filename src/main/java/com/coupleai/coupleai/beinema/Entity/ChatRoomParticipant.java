package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "chat_room_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_room_user",
                        columnNames = {"chat_room_id", "user_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomParticipant extends BaseEntity {


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "chat_room_id",
            nullable = false
    )
    private ChatRoom chatRoom;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;


    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    @Builder.Default
    private ParticipantRole role = ParticipantRole.MEMBER;

    /**
     * Temporary session participation pause. Null or past = timer finished.
     * This is not an account ban.
     */
    @Column(name = "paused_until")
    private java.time.Instant pausedUntil;

    /**
     * After pause expires, the participant must accept a re-entry commitment
     * before sending again.
     */
    @Column(name = "reentry_pending")
    @Builder.Default
    private Boolean reentryPending = false;

    @Column(name = "commitment_offered")
    @Builder.Default
    private Boolean commitmentOffered = false;

    @Column(name = "reentry_handshake")
    @Builder.Default
    private Boolean reentryHandshake = false;

    /**
     * False = must accept session rules before sending.
     * Null = legacy participant, already allowed.
     */
    @Column(name = "session_consented")
    private Boolean sessionConsented;

    @Column(name = "consent_offered")
    @Builder.Default
    private Boolean consentOffered = false;

}