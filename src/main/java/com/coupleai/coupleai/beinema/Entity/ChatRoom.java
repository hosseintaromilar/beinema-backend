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


    @OneToMany(
            mappedBy = "chatRoom",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<ChatRoomParticipant> participants =
            new ArrayList<>();


    @OneToMany(
            mappedBy = "chatRoom",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<ChatRoomAgent> agents =
            new ArrayList<>();


    @OneToMany(
            mappedBy = "chatRoom",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<Message> messages =
            new ArrayList<>();


    @Column(nullable = false)
    private Long createdBy;

}