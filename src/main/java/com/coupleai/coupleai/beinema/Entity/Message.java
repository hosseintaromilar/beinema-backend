package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(
        name = "messages",
        indexes = {
                @Index(
                        name = "idx_message_chat_room",
                        columnList = "chat_room_id"
                ),
                @Index(
                        name = "idx_message_created_at",
                        columnList = "createdAt"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "chat_room_id",
            nullable = false
    )
    private ChatRoom chatRoom;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageSenderType senderType;


    @Column(nullable = false)
    private Long senderId;


    @Lob
    @Column(nullable = false)
    private String content;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;


    @Column(nullable = false)
    @Builder.Default
    private Integer sequenceNumber = 0;


    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

}