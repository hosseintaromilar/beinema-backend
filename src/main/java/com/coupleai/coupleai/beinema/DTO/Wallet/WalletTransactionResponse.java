package com.coupleai.coupleai.beinema.DTO.Wallet;

import com.coupleai.coupleai.beinema.Enum.WalletTransactionStatus;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WalletTransactionResponse {

    private Long id;

    private WalletTransactionType type;

    private WalletTransactionStatus status;

    private Long amount;

    private String description;

    private String reference;

    private Long createdAt;
}
