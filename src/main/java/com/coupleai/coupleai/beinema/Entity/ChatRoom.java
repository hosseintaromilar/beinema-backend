package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom extends BaseEntity {


    @Column(nullable = false)
    private String title;

    /** Optional: what the creator wrote about why they open this conversation. */
    @Column(name = "creator_note", length = 500)
    private String creatorNote;

    /** How far the companion has picked up and read the room (message sequence numbers). Written only by the receipt queries: saving a stale copy of this row must not put old numbers back. */
    @Column(name = "agent_delivered_seq", updatable = false)
    private Integer agentDeliveredSeq;

    @Column(name = "agent_read_seq", updatable = false)
    private Integer agentReadSeq;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ChatRoomStatus status = ChatRoomStatus.ACTIVE;


    /**
     * شناسه Conversation در سرویس AI Provider
     * مثل Metis / OpenAI / سایر LLM Providers
     * هر ChatRoom در سیستم ما
     * معادل یک Conversation مستقل در سرویس AI است.
     */
    @Column(
            unique = true
    )
    private String aiConversationId;


    /**
     * شناسه Bot یا Workflow متیس که این جلسه با آن ساخته شده.
     * اگر با ai_bot_id فعلی ایجنت فرق کند، جلسه متیس از نو ساخته می‌شود.
     */
    @Column(name = "ai_bot_id")
    private String aiBotId;


    @OneToMany(
            mappedBy = "chatRoom"
    )
    @Builder.Default
    private List<ChatRoomParticipant> participants =
            new ArrayList<>();


    @OneToMany(
            mappedBy = "chatRoom"
    )
    @Builder.Default
    private List<ChatRoomAgent> agents =
            new ArrayList<>();


    @OneToMany(
            mappedBy = "chatRoom"
    )
    @Builder.Default
    private List<Message> messages =
            new ArrayList<>();


    @Column(nullable = false)
    private Long createdBy;


    /** Which counseling session of this room is running (1, 2, ...). Null = 1. */
    @Column(name = "session_number")
    private Integer sessionNumber;

    /** False between two sessions, after both partners agreed to a break. Null = active. */
    @Column(name = "session_active")
    private Boolean sessionActive;

    /** When the last session ended; the next one may begin after the minimum break. */
    @Column(name = "session_ended_at")
    private java.time.Instant sessionEndedAt;

    /** Start of the 45-minute countdown: when both partners agreed to begin. Null until then. */
    @Column(name = "session_started_at")
    private java.time.Instant sessionStartedAt;

    /** Overrides beinema.session.duration-minutes for this room. */
    @Column(name = "session_duration_minutes")
    private Integer sessionDurationMinutes;

    /** Set while the counselor's break proposal waits for both answers. */
    @Column(name = "break_proposed_at")
    private java.time.Instant breakProposedAt;

    /** When a partner first said no to the break offer of this session (they went on). */
    @Column(name = "break_declined_at")
    private java.time.Instant breakDeclinedAt;

    /** Last message number before this session began (the summary covers what came after). */
    @Column(name = "session_start_sequence")
    private Integer sessionStartSequence;

    /** Brief for the first message of a new session; cleared once it reached the counselor. */
    @Column(name = "resume_block", columnDefinition = "TEXT")
    private String resumeBlock;

    /** When the owner ended the conversation for good (status ENDED): nobody writes any more. */
    @Column(name = "ended_at")
    private java.time.Instant endedAt;

    @Column(name = "ended_by")
    private Long endedBy;

}