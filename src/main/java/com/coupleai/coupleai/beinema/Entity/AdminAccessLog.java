package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Which admin looked at whose account or chat, and when. Rows are only ever added. */
@Entity
@Table(name = "admin_access_log", indexes = @Index(name = "idx_admin_access_target", columnList = "target_user_id"))
@Getter
@Setter
@NoArgsConstructor
public class AdminAccessLog extends BaseEntity {

    public static final String VIEW_ACCOUNT = "VIEW_ACCOUNT";
    public static final String READ_CHAT = "READ_CHAT";

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(length = 20, nullable = false)
    private String action;
}
