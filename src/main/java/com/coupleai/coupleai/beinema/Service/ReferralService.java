package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Referral.ReferralPreviewResponse;
import com.coupleai.coupleai.beinema.DTO.Referral.ReferralProfileResponse;
import com.coupleai.coupleai.beinema.DTO.Referral.ReferralRewardItem;
import com.coupleai.coupleai.beinema.Entity.Referral;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import com.coupleai.coupleai.beinema.Repository.ReferralRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Util.ReferralCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReferralService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ReferralRepository referralRepository;
    private final WalletService walletService;
    private final long referrerBonus;
    private final long refereeBonus;
    private final int dailyCap;
    private final int monthlyCap;

    public ReferralService(
            UserRepository userRepository,
            ReferralRepository referralRepository,
            WalletService walletService,
            @Value("${beinema.referral.referrer-bonus:50000}") long referrerBonus,
            @Value("${beinema.referral.referee-bonus:50000}") long refereeBonus,
            @Value("${beinema.referral.daily-cap:10}") int dailyCap,
            @Value("${beinema.referral.monthly-cap:10}") int monthlyCap
    ) {
        this.monthlyCap = Math.max(0, monthlyCap);
        this.userRepository = userRepository;
        this.referralRepository = referralRepository;
        this.walletService = walletService;
        this.referrerBonus = Math.max(0, referrerBonus);
        this.refereeBonus = Math.max(0, refereeBonus);
        this.dailyCap = Math.max(0, dailyCap);
    }

    @Transactional
    public ReferralProfileResponse profile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("کاربر پیدا نشد"));
        ensureCode(user);
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long rewardedToday = referralRepository
                .countByReferrerUserIdAndReferrerAmountGreaterThanAndCreatedAtAfter(
                        user.getId(),
                        0L,
                        startOfDay
                );
        List<Referral> recent = referralRepository
                .findTop10ByReferrerUserIdOrderByCreatedAtDesc(user.getId());
        long totalInvited = referralRepository.countByReferrerUserId(user.getId());
        return ReferralProfileResponse.builder()
                .code(user.getReferralCode())
                .referrerBonus(referrerBonus)
                .refereeBonus(refereeBonus)
                .dailyCap(dailyCap)
                .rewardedToday(rewardedToday)
                .totalInvited(totalInvited)
                .totalEarned(referralRepository.sumReferrerAmount(user.getId()))
                .recent(recent.stream().map(this::toItem).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public ReferralPreviewResponse preview(String rawCode) {
        String code = ReferralCodes.normalize(rawCode);
        if (code.isEmpty()) {
            return ReferralPreviewResponse.builder()
                    .valid(false)
                    .referrerName("")
                    .refereeBonus(0)
                    .build();
        }
        return userRepository.findByReferralCode(code)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && !user.isDeleted())
                .map(user -> ReferralPreviewResponse.builder()
                        .valid(true)
                        .referrerName(user.visibleName())
                        .refereeBonus(refereeBonus)
                        .build())
                .orElseGet(() -> ReferralPreviewResponse.builder()
                        .valid(false)
                        .referrerName("")
                        .refereeBonus(0)
                        .build());
    }

    @Transactional
    public void rewardNewUser(User newUser, String rawCode) {
        if (newUser == null || newUser.getId() == null) {
            return;
        }
        if (referralRepository.existsByReferredUserId(newUser.getId())) {
            return;
        }
        String code = ReferralCodes.normalize(rawCode);
        if (code.isEmpty()) {
            return;
        }
        User referrer = userRepository.findByReferralCode(code).orElse(null);
        if (referrer == null
                || referrer.getId().equals(newUser.getId())
                || referrer.getStatus() != UserStatus.ACTIVE
                || referrer.isDeleted()
                || samePhone(referrer, newUser)) {
            return;
        }

        // nothing is given at sign-up: the gift comes when the new person buys or finishes a session
        referralRepository.saveAndFlush(Referral.builder()
                .referrerUserId(referrer.getId())
                .referredUserId(newUser.getId())
                .code(code)
                .referrerAmount(0L)
                .refereeAmount(0L)
                .status(PENDING)
                .build());
    }

    public static final String PENDING = "PENDING";
    public static final String REWARDED = "REWARDED";

    /**
     * The invited person bought a plan or finished a session: both get one free session, once.
     * A referrer gets at most monthlyCap gifts in 30 days (a guard against made-up accounts).
     */
    @Transactional
    public void qualify(User invitee) {
        if (invitee == null || invitee.getId() == null) {
            return;
        }
        Referral referral = referralRepository.findByReferredUserIdAndStatus(invitee.getId(), PENDING).orElse(null);
        if (referral == null) {
            return;
        }
        User referrer = userRepository.findById(referral.getReferrerUserId()).orElse(null);
        referral.setStatus(REWARDED);
        referral.setRewardedAt(java.time.Instant.now());
        referralRepository.save(referral);
        long lately = referrer == null ? 0 : referralRepository.countByReferrerUserIdAndStatusAndRewardedAtAfter(
                referrer.getId(), REWARDED, java.time.Instant.now().minus(java.time.Duration.ofDays(30)));
        gift(invitee);
        if (referrer != null && lately <= monthlyCap) {
            gift(referrer);
        }
        if (giftNotice != null) {
            giftNotice.accept(invitee);
            if (referrer != null && lately <= monthlyCap) {
                giftNotice.accept(referrer);
            }
        }
    }

    private void gift(User user) {
        User fresh = userRepository.findById(user.getId()).orElse(user);
        fresh.setGiftedSessions((fresh.getGiftedSessions() == null ? 0 : fresh.getGiftedSessions()) + 1);
        userRepository.save(fresh);
    }

    private java.util.function.Consumer<User> giftNotice;

    /** Set by the inbox: a note to each person who got a gift. */
    public void onGift(java.util.function.Consumer<User> listener) {
        this.giftNotice = listener;
    }

    private void pay(User user, long amount, String description, String reference) {
        walletService.credit(
                user,
                amount,
                WalletTransactionType.REFERRAL_BONUS,
                description,
                reference
        );
    }

    private ReferralRewardItem toItem(Referral referral) {
        long joinedAt = referral.getCreatedAt() == null
                ? 0L
                : referral.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return ReferralRewardItem.builder()
                .joinedAt(joinedAt)
                .reward(safe(referral.getReferrerAmount()))
                .build();
    }

    private void ensureCode(User user) {
        if (user.getReferralCode() != null && !user.getReferralCode().isBlank()) {
            return;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            String code = ReferralCodes.generate(RANDOM);
            if (!userRepository.existsByReferralCode(code)) {
                user.setReferralCode(code);
                userRepository.save(user);
                return;
            }
        }
        throw new IllegalStateException("ساخت کد دعوت ممکن نشد");
    }

    private static boolean samePhone(User referrer, User referred) {
        return referrer.getPhoneNumber() != null
                && referrer.getPhoneNumber().equals(referred.getPhoneNumber());
    }

    private static long safe(Long amount) {
        return amount == null ? 0L : amount;
    }
}
