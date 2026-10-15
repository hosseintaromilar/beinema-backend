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


    /**
     * شمارهٔ آخرین پیام اتاق در لحظهٔ ساخت نشست فعلی متیس. پیام‌های بعد از آن
     * هزینهٔ تاریخچهٔ نشست را می‌سازند؛ با طولانی شدن، نشست عوض می‌شود.
     */
    @Column(name = "ai_session_start_sequence")
    private Integer aiSessionStartSequence;


    /** خلاصهٔ خصوصی جلسه تا آخرین عوض شدن نشست (برای ادامه‌ی همان جلسه). */
    @Column(name = "ai_session_brief", columnDefinition = "TEXT")
    private String aiSessionBrief;


    /** خلاصه‌ای که هنوز به نشست تازهٔ متیس داده نشده و با پیام بعدی می‌رود. */
    @Column(name = "ai_resume_block", columnDefinition = "TEXT")
    private String aiResumeBlock;


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

}