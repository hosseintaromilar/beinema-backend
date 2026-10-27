package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.TopUpReceiptStatus;
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

/**
 * A card-to-card payment the person made and sent the receipt of. An admin
 * looks at the receipt and approves it (the wallet is charged) or rejects it.
 */
@Entity
@Table(name = "top_up_receipt", indexes = {
        @Index(name = "idx_top_up_receipt_user", columnList = "user_id"),
        @Index(name = "idx_top_up_receipt_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class TopUpReceipt extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** What they say they paid, in toman. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** What the admin charged; usually the same as amount. */
    @Column(name = "approved_amount")
    private Long approvedAmount;

    /** The bank's tracking number from the receipt, if they wrote it. */
    @Column(name = "tracking_code", length = 40)
    private String trackingCode;

    /** Under the upload dir, receipts/...; never served publicly. */
    @Column(name = "storage_path", nullable = false, length = 255)
    private String storagePath;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TopUpReceiptStatus status = TopUpReceiptStatus.PENDING;

    /** Why it was rejected (shown to the person) or a note on the approval. */
    @Column(name = "admin_note", length = 300)
    private String adminNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;
}
