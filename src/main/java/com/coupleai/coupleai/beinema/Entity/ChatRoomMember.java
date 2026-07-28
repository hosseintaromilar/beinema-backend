package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.ChatRoomMemberStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_room_members",

        uniqueConstraints = {

                @UniqueConstraint(

                        name = "uk_chat_room_user",

                        columnNames = {

                                "chat_room_id",

                                "user_id"

                        }

                )

        }

)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomMember extends BaseEntity {


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
    @Column(nullable = false)
    @Builder.Default
    private ChatRoomMemberStatus status = ChatRoomMemberStatus.ACTIVE;


    @Column(nullable = false)
    private LocalDateTime joinedAt;


    @PrePersist
    protected void onCreate() {

        if (joinedAt == null) {

            joinedAt = LocalDateTime.now();

        }

    }

}