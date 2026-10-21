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

    /**
     * When this person entered the current session (the consent in the first session,
     * the start button in later ones). Null = not in the session. Cleared when a session ends.
     */
    @Column(name = "session_ready_at")
    private java.time.Instant sessionReadyAt;

    /**
     * Answer to the break offer at the end of the 45 minutes: null = not answered,
     * true = takes the break (does not write until the session ends), false = goes on.
     */
    @Column(name = "break_vote")
    private Boolean breakVote;

    /** The last time this person opened the room (the first session waits while they read the rules). */
    @Column(name = "entered_at")
    private java.time.Instant enteredAt;

}