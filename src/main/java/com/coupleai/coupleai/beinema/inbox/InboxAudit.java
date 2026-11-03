package com.coupleai.coupleai.beinema.inbox;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Who did what with an inbox message, and when. Rows are only ever added. */
@Entity
@Table(name = "inbox_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class InboxAudit extends BaseEntity {

    @Column(name = "actor_id")
    private Long actorId;

    @Column(length = 20, nullable = false)
    private String action;

    @Column(name = "message_id")
    private Long messageId;

    @Column(length = 300)
    private String details;

    @Column(length = 64)
    private String ip;
}
