package com.coupleai.coupleai.beinema.inbox;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One message of the users' inbox: from the system, an announcement or a personal note from the
 * admin. It never carries anything of a couple's conversation. Who received it is in InboxRecipient,
 * fixed when it was sent.
 */
@Entity
@Table(name = "inbox_messages")
@Getter
@Setter
@NoArgsConstructor
public class InboxMessage extends BaseEntity {

    public static final String SYSTEM = "system";
    public static final String ANNOUNCEMENT = "announcement";
    public static final String PERSONAL = "personal";

    public static final String SCHEDULED = "SCHEDULED";
    public static final String SENT = "SENT";
    public static final String CANCELLED = "CANCELLED";
    public static final String REVOKED = "REVOKED";

    @Column(length = 15, nullable = false)
    private String type;

    @Column(length = 80, nullable = false)
    private String title;

    @Column(length = 2000, nullable = false)
    private String body;

    @Column(name = "cta_label", length = 40)
    private String ctaLabel;

    /** A path inside the site, such as /dashboard/wallet. */
    @Column(name = "cta_path", length = 120)
    private String ctaPath;

    /** normal or important. */
    @Column(length = 10, nullable = false)
    private String priority = "normal";

    @Column(name = "sender_id")
    private Long senderId;

    /** user, segment or all. */
    @Column(name = "audience_kind", length = 10, nullable = false)
    private String audienceKind;

    /** The segment and its value, as the admin chose it (for the history). */
    @Column(name = "audience_filter", length = 200)
    private String audienceFilter;

    @Column(name = "recipient_count")
    private Integer recipientCount;

    @Column(length = 10, nullable = false)
    private String status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** A double click sends once. */
    @Column(name = "idempotency_key", length = 64, unique = true)
    private String idempotencyKey;
}
