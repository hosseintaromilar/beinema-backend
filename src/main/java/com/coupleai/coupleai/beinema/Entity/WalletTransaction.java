package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.WalletTransactionStatus;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WalletTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private WalletTransactionStatus status = WalletTransactionStatus.SUCCESS;

    @Column(nullable = false)
    private Long amount;

    @Column(length = 80)
    private String reference;

    @Column(length = 255)
    private String description;

    private Long relatedChatRoomId;

    private Long relatedAgentAccessId;
}
