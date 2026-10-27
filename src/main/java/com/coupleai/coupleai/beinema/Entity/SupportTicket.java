package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.SupportTicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A person's request to support; its messages are SupportTicketMessage. */
@Entity
@Table(name = "support_ticket", indexes = {
        @Index(name = "idx_support_ticket_user", columnList = "user_id"),
        @Index(name = "idx_support_ticket_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class SupportTicket extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "subject", nullable = false, length = 150)
    private String subject;

    /** ACCOUNT, PAYMENT, CHAT, BUG or OTHER. */
    @Column(name = "category", nullable = false, length = 16)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    /** Support answered and the person has not opened it yet. */
    @Column(name = "unread_by_user", nullable = false)
    private boolean unreadByUser;
}
