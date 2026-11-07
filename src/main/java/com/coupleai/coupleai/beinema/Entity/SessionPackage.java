package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A bundle of sessions someone bought from their wallet, used one by one until it runs out or expires. */
@Entity
@Table(name = "session_package", indexes = @Index(name = "idx_session_package_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class SessionPackage extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The plan: TRIAL, MAIN, DIAMOND or MONTHLY (SessionPackageTier); older rows may hold BRONZE and the like. */
    @Column(name = "tier", nullable = false, length = 16)
    private String tier;

    @Column(name = "sessions_total", nullable = false)
    private Integer sessionsTotal;

    @Column(name = "sessions_used", nullable = false)
    private Integer sessionsUsed = 0;

    /** What was paid, in toman. */
    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** The discount step the price came from (1 or 2); null for the regular price. */
    @Column(name = "promo_tier")
    private Integer promoTier;

    /** The plan's regular price when it was bought, for the refund rule. */
    @Column(name = "regular_price")
    private Long regularPrice;

    /** A subscription renews from the wallet at the end of its month until cancelled. */
    @Column(name = "auto_renew")
    private Boolean autoRenew;

    /** This month came from a renewal (it did not take a discount place). */
    @Column(name = "renewal")
    private Boolean renewal;

    /** The next month was already made from this one. */
    @Column(name = "renewed")
    private Boolean renewed;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** What is paid back after a cancellation, in toman. */
    @Column(name = "refund_amount")
    private Long refundAmount;

    /** PENDING until the admin has paid it back to the person's bank account, then PAID. */
    @Column(name = "refund_status", length = 10)
    private String refundStatus;

    @Column(name = "refund_paid_at")
    private Instant refundPaidAt;

    /** CARD: paid back to the person's bank card by the admin; WALLET: back to their credit at once. */
    @Column(name = "refund_method", length = 10)
    private String refundMethod;

    /** The admin's note on the cancellation (the ticket, the reason). */
    @Column(name = "refund_note", length = 300)
    private String refundNote;

    public int sessionsLeft() {
        return Math.max(0, sessionsTotal - (sessionsUsed == null ? 0 : sessionsUsed));
    }
}
