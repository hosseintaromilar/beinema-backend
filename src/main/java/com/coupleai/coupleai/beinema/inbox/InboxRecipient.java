package com.coupleai.coupleai.beinema.inbox;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One person's copy of an inbox message: read, archived or deleted from their own view. */
@Entity
@Table(name = "inbox_recipients",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"}),
        indexes = @Index(name = "idx_inbox_recipient_user", columnList = "user_id,read_at"))
@Getter
@Setter
@NoArgsConstructor
public class InboxRecipient extends BaseEntity {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Gone from the person's inbox; the row stays on the server. */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
