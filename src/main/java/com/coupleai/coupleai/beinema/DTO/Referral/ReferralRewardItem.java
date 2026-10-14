package com.coupleai.coupleai.beinema.DTO.Referral;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReferralRewardItem {

    private final long joinedAt;
    private final long reward;
}
