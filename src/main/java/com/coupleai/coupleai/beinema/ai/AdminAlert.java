package com.coupleai.coupleai.beinema.ai;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Something the admin should know at once: the AI went down or came back, or a person reported a
 * picture. Shown in the admin panel; no words of any conversation are kept.
 */
@Entity
@Table(name = "admin_alerts")
@Getter
@Setter
@NoArgsConstructor
public class AdminAlert extends BaseEntity {

    public static final String NO_CREDIT = "no_credit";
    public static final String ERROR_SPIKE = "error_spike";
    public static final String BAD_REQUEST = "bad_request";
    public static final String RECOVERED = "recovered";
    public static final String IMAGE_REPORT = "image_report";

    @Column(length = 30, nullable = false)
    private String kind;

    @Column(length = 300)
    private String detail;

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "seen", nullable = false)
    private boolean seen;
}
