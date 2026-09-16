package com.coupleai.coupleai.beinema.DTO.Wallet;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WalletResponse {

    private Long balance;

    private String currency;

    private List<Long> topUpPresets;
}
