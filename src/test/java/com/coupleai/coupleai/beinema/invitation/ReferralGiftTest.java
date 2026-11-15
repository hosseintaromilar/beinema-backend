package com.coupleai.coupleai.beinema.invitation;

import com.coupleai.coupleai.beinema.Entity.Referral;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Repository.ReferralRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.billing.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReferralGiftTest {

    private final UserRepository users = mock(UserRepository.class);
    private final ReferralRepository referrals = mock(ReferralRepository.class);
    private final List<User> noticed = new ArrayList<>();
    private User inviter;
    private ReferralService service;
    private long giftedThisMonth;

    private static User user(long id, String phone) {
        User user = new User();
        user.setId(id);
        user.setPhoneNumber(phone);
        user.setStatus(UserStatus.ACTIVE);
        user.setGiftedSessions(0);
        return user;
    }

    @BeforeEach
    void setUp() {
        inviter = user(1L, "09120000001");
        inviter.setReferralCode("ABCD2345");
        when(users.findByReferralCode("ABCD2345")).thenReturn(Optional.of(inviter));
        when(users.findById(1L)).thenReturn(Optional.of(inviter));
        when(referrals.saveAndFlush(any(Referral.class))).thenAnswer(call -> call.getArgument(0));
        when(referrals.countByReferrerUserIdAndStatusAndRewardedAtAfter(eq(1L), anyString(), any()))
                .thenAnswer(call -> ++giftedThisMonth);
        service = new ReferralService(users, referrals, mock(WalletService.class), 0, 0, 10, 10);
        service.onGift(noticed::add);
    }

    @Test
    void theOneWhoInvitedGetsASessionAtSignUpAndTheNewPersonNothingMore() {
        User newcomer = user(2L, "09120000002");
        when(users.findById(2L)).thenReturn(Optional.of(newcomer));

        service.rewardNewUser(newcomer, "abcd2345");

        assertEquals(1, inviter.getGiftedSessions(), "one free session for the one who invited");
        assertEquals(0, newcomer.getGiftedSessions(), "the newcomer has the ordinary free first session only");
        assertEquals(List.of(inviter), noticed, "only the one who invited is told");
    }

    @Test
    void atMostTenGiftsInAMonth() {
        for (long id = 2; id <= 13; id++) {
            when(referrals.existsByReferredUserId(anyLong())).thenReturn(false);
            service.rewardNewUser(user(id, "0912000" + (1000 + id)), "ABCD2345");
        }
        assertEquals(10, inviter.getGiftedSessions(), "twelve sign-ups, ten gifts");
    }
}
