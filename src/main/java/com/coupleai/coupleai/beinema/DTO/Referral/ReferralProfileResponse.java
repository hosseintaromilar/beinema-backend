package com.coupleai.coupleai.beinema.DTO.Referral;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReferralProfileResponse {

    private final String code;
    private final long referrerBonus;
    private final long refereeBonus;
    private final int dailyCap;
    private final long rewardedToday;
    private final long totalInvited;
    private final long totalEarned;
    private final List<ReferralRewardItem> recent;
}
