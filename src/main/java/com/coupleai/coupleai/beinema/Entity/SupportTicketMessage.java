package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One message in a support ticket, from the person or from support. */
@Entity
@Table(name = "support_ticket_message", indexes = @Index(name = "idx_support_message_ticket", columnList = "ticket_id"))
@Getter
@Setter
@NoArgsConstructor
public class SupportTicketMessage extends BaseEntity {

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "from_support", nullable = false)
    private boolean fromSupport;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;
}
