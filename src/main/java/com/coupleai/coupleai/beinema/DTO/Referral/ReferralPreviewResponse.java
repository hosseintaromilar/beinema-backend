package com.coupleai.coupleai.beinema.DTO.Referral;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReferralPreviewResponse {

    private final boolean valid;
    private final String referrerName;
    private final long refereeBonus;
}
