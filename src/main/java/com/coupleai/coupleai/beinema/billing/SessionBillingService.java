package com.coupleai.coupleai.beinema.billing;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.SessionPackage;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Entity.Wallet;
import com.coupleai.coupleai.beinema.Enum.SessionPackageTier;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import com.coupleai.coupleai.beinema.Exception.SessionOnBreakException;
import com.coupleai.coupleai.beinema.Repository.SessionPackageRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Who pays for a session: the person who created the conversation. Their first
 * session is free (one, once, across all their conversations); then a session
 * comes from a bundle they bought (SessionPackageTier, valid for a month), and
 * otherwise costs the single session price from their wallet, which they charge
 * by card-to-card (TopUpReceiptService). A session is settled when it closes
 * with a conversation in it, so an empty session costs nothing; before one
 * opens, the creator must have a free session, a bundle or credit for it.
 */
@Service
@RequiredArgsConstructor
public class SessionBillingService {

    private static final Logger log = LoggerFactory.getLogger(SessionBillingService.class);

    public static final String FREE = "FREE";
    public static final String PAID = "PAID";
    public static final String UNPAID = "UNPAID";
    /** Taken from a session bundle (SessionPackage). */
    public static final String PACKAGE = "PACKAGE";
    /** Not charged: the AI service was down for a good part of the session. */
    public static final String OUTAGE = "OUTAGE";

    static final String VERIFY_FIRST = "اول ایمیلت را در «پروفایل» ثبت و تأیید کن؛ جلسهٔ رایگان و خرید بسته بعد از تأیید ایمیل فعال می‌شود.";

    private final UserRepository userRepository;
    private final com.coupleai.coupleai.beinema.ai.AiHealth aiHealth;

    /** A session that stood still this long for an outage is not charged (free sessions too). */
    @Value("${beinema.billing.outage-free-seconds:300}")
    private long outageFreeSeconds;
    private final WalletService walletService;
    private final SessionPackageRepository packageRepository;
    private final com.coupleai.coupleai.beinema.Repository.ChatRoomRepository chatRoomRepository;
    private final com.coupleai.coupleai.beinema.Repository.PromoTierCounterRepository counterRepository;

    private static final Long NO_ROOM = -1L;

    @Value("${beinema.billing.free-sessions:1}")
    private int freeSessions;

    @Value("${beinema.billing.session-price:100000}")
    private long sessionPrice;

    /** The usual price, shown struck through while the launch price is on (at most twice the price). */
    @Value("${beinema.billing.session-list-price:200000}")
    private long sessionListPrice;

    /** Sessions a conversation may hold in 24 hours when its owner has no bundle. */
    @Value("${beinema.billing.daily-sessions:1}")
    private int dailySessionsWithoutBundle;

    @Value("${beinema.billing.card-number:6037997135935907}")
    private String cardNumber;

    @Value("${beinema.billing.card-holder:حسین طارمیلر}")
    private String cardHolder;

    @Value("${beinema.billing.bank-name:بانک ملی}")
    private String bankName;

    /**
     * What the wallet page shows: free sessions left, the price, the bundles
     * on sale and the ones bought, and where to pay.
     */
    public record Offer(int freeSessions, int freeLeft, long sessionPrice, long sessionListPrice, long balance,
                        boolean nextIsFree, boolean canStart, String cardNumber, String cardHolder, String bankName,
                        List<PackageOffer> packages, List<OwnedPackage> myPackages, int dailySessions) {
    }

    /** A bundle on sale at its active step; listPrice (struck through) is the regular price. */
    public record PackageOffer(String tier, String title, int sessions, long price, long listPrice,
                               long perSession, int validDays, int dailySessions, String perk) {
    }

    /** One discount step of a plan with its real counter. */
    public record StepView(int tierNo, int pct, long price, int sold, int capacity) {
    }

    /** A plan as the pricing cards show it (GET /api/pricing): nothing personal in it. */
    public record PlanView(String planId, String title, int sessions, long regularPrice, int validDays,
                           int dailyCap, boolean refundable, boolean recurring, Integer activeTier,
                           long activePrice, long perSession, long regularPerSession, List<StepView> tiers,
                           boolean available) {
    }

    /** A bundle someone bought that still has sessions and time left. */
    public record OwnedPackage(Long id, String tier, String title, int sessionsLeft, int sessionsTotal, long expiresAt,
                               boolean refundable, boolean recurring, boolean autoRenew, long refundIfCancelled,
                               long price) {
    }

    /** The public part, for the counselor pages: free sessions, the price and the bundles. */
    public record Prices(int freeSessions, long sessionPrice, long sessionListPrice, int dailySessions,
                         List<PackageOffer> packages) {
    }

    public Prices prices() {
        return new Prices(freeSessions, sessionPrice, sessionListPrice, dailySessionsWithoutBundle, packageOffers());
    }

    public List<PackageOffer> packageOffers() {
        return pricing().stream()
                .map(plan -> new PackageOffer(plan.planId(), plan.title(), plan.sessions(), plan.activePrice(),
                        plan.regularPrice(), plan.perSession(), plan.validDays(), plan.dailyCap(),
                        plan.refundable() ? "امکان لغو در هر زمان، با بازگشت وجه جلسات استفاده‌نشده."
                                : plan.recurring() ? "قابل لغو در هر ماه." : null))
                .toList();
    }

    /** Every plan with its real counters and the step a buyer would get now (nobody in particular). */
    @Transactional(readOnly = true)
    public List<PlanView> pricing() {
        return pricing(null);
    }

    /**
     * The same for one person: a step they already bought at is skipped, as the purchase would skip
     * it, so the card shows the price they would really pay; the trial is not offered twice.
     */
    @Transactional(readOnly = true)
    public List<PlanView> pricing(User buyer) {
        return Arrays.stream(SessionPackageTier.values()).map(plan -> {
            List<StepView> steps = plan.steps().stream().map(step -> {
                int sold = counterRepository.findByPlanAndTierNo(plan.name(), step.number())
                        .map(counter -> counter.getSold()).orElse(0);
                return new StepView(step.number(), step.percent(), step.price(), Math.min(sold, step.capacity()), step.capacity());
            }).toList();
            StepView active = steps.stream()
                    .filter(step -> step.sold() < step.capacity())
                    .filter(step -> buyer == null || !packageRepository.existsByUserIdAndTierAndPromoTier(
                            buyer.getId(), plan.name(), step.tierNo()))
                    .findFirst().orElse(null);
            long price = active == null ? plan.regularPrice() : active.price();
            boolean available = buyer == null || plan != SessionPackageTier.TRIAL
                    || !packageRepository.existsByUserIdAndTier(buyer.getId(), plan.name());
            return new PlanView(plan.name(), plan.title(), plan.sessions(), plan.regularPrice(), plan.validDays(),
                    plan.dailySessions(), plan.refundable(), plan.recurring(), active == null ? null : active.tierNo(),
                    price, price / plan.sessions(), plan.regularPerSession(), steps, available);
        }).toList();
    }

    /** The length of the free first session, in minutes. */
    @Value("${beinema.billing.free-session-minutes:20}")
    private int freeSessionMinutes;

    /** Minutes of the next session of this creator: short when it will be free, else the usual (null). */
    @Transactional(readOnly = true)
    public Integer sessionMinutesFor(Long ownerId) {
        if (ownerId == null || freeSessionMinutes <= 0) {
            return null;
        }
        User owner = userRepository.findById(ownerId).orElse(null);
        if (owner == null || !packageRepository.findUsable(ownerId, Instant.now()).isEmpty()) {
            // a session from a bundle is a full one
            return null;
        }
        return freeLeft(owner) > 0 ? freeSessionMinutes : null;
    }

    /** Sessions a day without a bundle (the rules and the welcome say it). */
    public int dailySessionsWithoutBundle() {
        return dailySessionsWithoutBundle;
    }

    /**
     * Sessions a conversation may hold in 24 hours: the best active bundle of
     * its owner decides, and without one the plain limit.
     */
    @Transactional(readOnly = true)
    public int dailyLimitFor(Long ownerId) {
        if (ownerId == null) {
            return dailySessionsWithoutBundle;
        }
        return packageRepository.findUsable(ownerId, Instant.now()).stream()
                .map(item -> {
                    SessionPackageTier plan = SessionPackageTier.of(item.getTier());
                    return plan == null ? dailySessionsWithoutBundle : plan.dailySessions();
                })
                .max(Integer::compare)
                .orElse(dailySessionsWithoutBundle);
    }

    @Transactional
    public Offer offerFor(User user) {
        int left = freeAvailable(user, NO_ROOM);
        long balance = walletService.getOrCreate(user).getBalance();
        List<OwnedPackage> mine = packageRepository.findUsable(user.getId(), Instant.now()).stream()
                .map(this::owned)
                .toList();
        return new Offer(freeSessions, left, sessionPrice, sessionListPrice, balance,
                left > 0, left > 0 || !mine.isEmpty() || balance >= sessionPrice, cardNumber, cardHolder, bankName,
                packageOffers(), mine, dailySessionsWithoutBundle);
    }

    /**
     * Buys a plan with the wallet's credit. The price is decided here, in this transaction: the
     * first discount step with a place left that this person has not used for this plan, else the
     * regular price. When the price is not the one the person saw (expectedPrice), nothing is
     * bought and they are told the new price; more is never taken silently.
     */
    @Transactional
    public OwnedPackage buyPackage(User user, String tierName, Long expectedPrice) {
        SessionPackageTier plan = SessionPackageTier.of(tierName);
        if (plan == null) {
            throw new IllegalArgumentException("این بسته پیدا نشد.");
        }
        if (!confirmed(user)) {
            throw new IllegalArgumentException(VERIFY_FIRST);
        }
        if (plan == SessionPackageTier.TRIAL && packageRepository.existsByUserIdAndTier(user.getId(), plan.name())) {
            throw new IllegalArgumentException("بستهٔ آزمایشی برای هر حساب فقط یک بار است؛ برای ادامه یکی از بسته‌های دیگر را انتخاب کن.");
        }
        Integer step = null;
        long price = plan.regularPrice();
        for (SessionPackageTier.Step candidate : plan.steps()) {
            if (packageRepository.existsByUserIdAndTierAndPromoTier(user.getId(), plan.name(), candidate.number())) {
                continue;
            }
            if (counterRepository.take(plan.name(), candidate.number()) == 1) {
                step = candidate.number();
                price = candidate.price();
                break;
            }
        }
        if (expectedPrice != null && expectedPrice != price) {
            // rolls back the place just taken
            throw new PriceChangedException("قیمت " + plan.title() + " همین حالا عوض شد و الان " + toman(price)
                    + " است. اگر موافقی، دوباره خرید را بزن.", price);
        }
        Wallet wallet = walletService.lockByUserId(user.getId());
        if (wallet.getBalance() < price) {
            throw new IllegalArgumentException("اعتبار حسابت برای " + plan.title() + " کافی نیست؛ "
                    + toman(price) + " لازم است. اول حسابت را با کارت به کارت شارژ کن.");
        }
        walletService.debit(user, wallet, price, WalletTransactionType.PACKAGE_PURCHASE,
                "خرید " + plan.title() + " (" + digits(String.valueOf(plan.sessions())) + " جلسه)",
                "package:" + user.getId() + ":" + System.currentTimeMillis(), null, null);
        SessionPackage saved = packageRepository.save(newBundle(user.getId(), plan, price, step, false));
        purchased(user, plan, price);
        return owned(saved);
    }

    public OwnedPackage buyPackage(User user, String tierName) {
        return buyPackage(user, tierName, null);
    }

    private SessionPackage newBundle(Long userId, SessionPackageTier plan, long price, Integer step, boolean renewal) {
        SessionPackage bought = new SessionPackage();
        bought.setUserId(userId);
        bought.setTier(plan.name());
        bought.setSessionsTotal(plan.sessions());
        bought.setSessionsUsed(0);
        bought.setPrice(price);
        bought.setPromoTier(step);
        bought.setRegularPrice(plan.regularPrice());
        bought.setAutoRenew(plan.recurring());
        bought.setRenewal(renewal);
        bought.setExpiresAt(Instant.now().plus(Duration.ofDays(plan.validDays())));
        return bought;
    }

    /** The price changed between the card and the purchase; carries the new price for the site. */
    public static class PriceChangedException extends RuntimeException {
        private final long price;

        public PriceChangedException(String message, long price) {
            super(message);
            this.price = price;
        }

        public long price() {
            return price;
        }
    }

    /** Hook for what follows a purchase (the inbox note and the referral reward). */
    protected void purchased(User user, SessionPackageTier plan, long price) {
        if (afterPurchase != null) {
            afterPurchase.accept(user, plan.title() + " · " + toman(price));
        }
    }

    /** Set by the inbox and referrals; kept as a plain callback so billing depends on neither. */
    private java.util.function.BiConsumer<User, String> afterPurchase;

    public void onPurchase(java.util.function.BiConsumer<User, String> listener) {
        this.afterPurchase = afterPurchase == null ? listener : afterPurchase.andThen(listener);
    }

    /** What would be paid back if this bundle were cancelled now: its price less the used sessions at the regular price. */
    public static long refundFor(SessionPackage bundle) {
        SessionPackageTier plan = SessionPackageTier.of(bundle.getTier());
        if (plan == null || !plan.refundable()) {
            return 0;
        }
        long perSession = (bundle.getRegularPrice() == null ? plan.regularPrice() : bundle.getRegularPrice()) / plan.sessions();
        int used = bundle.getSessionsUsed() == null ? 0 : bundle.getSessionsUsed();
        return Math.max(0, bundle.getPrice() - used * perSession);
    }

    private OwnedPackage owned(SessionPackage item) {
        SessionPackageTier plan = SessionPackageTier.of(item.getTier());
        return new OwnedPackage(item.getId(), item.getTier(), titleOf(item.getTier()), item.sessionsLeft(),
                item.getSessionsTotal(), item.getExpiresAt().toEpochMilli(),
                plan != null && plan.refundable(), plan != null && plan.recurring(),
                Boolean.TRUE.equals(item.getAutoRenew()), refundFor(item), item.getPrice() == null ? 0 : item.getPrice());
    }

    /**
     * Cancels a bundle. Companion and three-month bundles stop at once and the refund is recorded
     * for the admin to pay back (card-to-card, so by hand); the discount place is not given back.
     * A subscription only stops renewing and stays usable to the end of its month. The trial has no refund.
     */
    @Transactional
    public OwnedPackage cancel(User user, Long packageId) {
        SessionPackage bundle = packageRepository.findById(packageId)
                .filter(item -> user.getId().equals(item.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("این بسته پیدا نشد."));
        SessionPackageTier plan = SessionPackageTier.of(bundle.getTier());
        if (bundle.getCancelledAt() != null || !bundle.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("این بسته دیگر فعال نیست.");
        }
        if (plan != null && plan.recurring()) {
            bundle.setAutoRenew(false);
            return owned(packageRepository.save(bundle));
        }
        // a refund is asked for with a support ticket; the admin cancels the bundle from there
        throw new IllegalArgumentException("برای بازگشت وجه از بخش «پشتیبانی» تیکت بزن.");
    }

    /** The admin, answering a refund ticket: the rule's amount, to the bank card. */
    @Transactional
    public OwnedPackage cancelForRefund(Long packageId) {
        return cancelForRefund(packageId, null, "CARD", null);
    }

    /**
     * The admin cancels a bundle after a refund: it stops at once (its sessions can no longer be used,
     * a subscription no longer renews) and the refund is recorded. amount: null for the rule's
     * amount, or what the admin decided (from nothing up to what was paid; also for a bundle the rule
     * gives nothing back). method: WALLET puts it back on the person's credit now; CARD waits for the
     * admin to pay it to their bank card. The person is told in their notifications.
     */
    @Transactional
    public OwnedPackage cancelForRefund(Long packageId, Long amount, String method, String note) {
        SessionPackage bundle = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("این بسته پیدا نشد."));
        if (bundle.getCancelledAt() != null || !bundle.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("این بسته دیگر فعال نیست.");
        }
        long paid = bundle.getPrice() == null ? 0 : bundle.getPrice();
        long refund = amount == null ? refundFor(bundle) : amount;
        if (refund < 0 || refund > paid) {
            throw new IllegalArgumentException("مبلغ بازگشت باید بین صفر و مبلغ خرید (" + toman(paid) + ") باشد.");
        }
        boolean toWallet = "WALLET".equals(method);
        Instant now = Instant.now();
        bundle.setCancelledAt(now);
        bundle.setExpiresAt(now);
        bundle.setAutoRenew(false);
        bundle.setRefundAmount(refund);
        bundle.setRefundMethod(toWallet ? "WALLET" : "CARD");
        bundle.setRefundNote(note == null || note.isBlank() ? null : note.strip().substring(0, Math.min(300, note.strip().length())));
        User user = userRepository.findById(bundle.getUserId()).orElse(null);
        if (refund == 0) {
            bundle.setRefundStatus("NONE");
        } else if (toWallet) {
            walletService.credit(user, refund, WalletTransactionType.REFUND,
                    "بازگشت وجه " + titleOf(bundle.getTier()), "refund:" + bundle.getId());
            bundle.setRefundStatus("PAID");
            bundle.setRefundPaidAt(now);
        } else {
            bundle.setRefundStatus("PENDING");
        }
        OwnedPackage result = owned(packageRepository.save(bundle));
        if (refundRecorded != null && user != null) {
            String what = titleOf(bundle.getTier()) + " لغو شد. "
                    + (refund == 0 ? "جلسه‌های مانده‌اش دیگر قابل استفاده نیست."
                    : toWallet ? toman(refund) + " به اعتبار حسابت برگشت."
                    : toman(refund) + " به کارت بانکی‌ات واریز می‌شود.");
            refundRecorded.accept(user, what);
        }
        return result;
    }

    /** The bundles of a person that were cancelled, newest first, for the admin's view of the account. */
    public List<java.util.Map<String, Object>> cancelledOf(Long userId) {
        return packageRepository.findAllByUserIdOrderByIdDesc(userId).stream()
                .filter(item -> item.getCancelledAt() != null)
                .map(item -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", item.getId());
                    row.put("title", titleOf(item.getTier()));
                    row.put("price", item.getPrice());
                    row.put("cancelledAt", item.getCancelledAt().toEpochMilli());
                    row.put("refundAmount", item.getRefundAmount());
                    row.put("refundStatus", item.getRefundStatus());
                    row.put("refundMethod", item.getRefundMethod());
                    row.put("refundNote", item.getRefundNote());
                    return row;
                })
                .toList();
    }

    /** Every bundle a person ever bought, newest first, with where it stands now: the admin's purchase history. */
    public List<java.util.Map<String, Object>> purchasesOf(Long userId) {
        Instant now = Instant.now();
        return packageRepository.findAllByUserIdOrderByIdDesc(userId).stream().map(item -> {
            String state = item.getCancelledAt() != null ? "CANCELLED"
                    : item.sessionsLeft() == 0 ? "USED"
                    : item.getExpiresAt() != null && item.getExpiresAt().isBefore(now) ? "EXPIRED"
                    : "ACTIVE";
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("title", titleOf(item.getTier()));
            row.put("state", state);
            row.put("price", item.getPrice());
            row.put("regularPrice", item.getRegularPrice());
            row.put("sessionsTotal", item.getSessionsTotal());
            row.put("sessionsUsed", item.getSessionsUsed() == null ? 0 : item.getSessionsUsed());
            row.put("sessionsLeft", item.sessionsLeft());
            row.put("boughtAt", item.getCreatedAt());
            row.put("expiresAt", item.getExpiresAt() == null ? null : item.getExpiresAt().toEpochMilli());
            row.put("renewal", Boolean.TRUE.equals(item.getRenewal()));
            row.put("autoRenew", Boolean.TRUE.equals(item.getAutoRenew()));
            return row;
        }).toList();
    }

    /** Monthly subscriptions renew from the wallet at the price they were bought at, until cancelled. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000L, initialDelay = 120_000L)
    @Transactional
    public void renewSubscriptions() {
        for (SessionPackage month : packageRepository.findToRenew(Instant.now().plus(Duration.ofHours(2)))) {
            SessionPackageTier plan = SessionPackageTier.of(month.getTier());
            User user = userRepository.findById(month.getUserId()).orElse(null);
            month.setRenewed(true);
            packageRepository.save(month);
            if (plan == null || user == null) {
                continue;
            }
            Wallet wallet = walletService.lockByUserId(user.getId());
            if (wallet.getBalance() < month.getPrice()) {
                month.setAutoRenew(false);
                packageRepository.save(month);
                if (renewalFailed != null) {
                    renewalFailed.accept(user, plan.title() + " · " + toman(month.getPrice()));
                }
                continue;
            }
            walletService.debit(user, wallet, month.getPrice(), WalletTransactionType.PACKAGE_PURCHASE,
                    "تمدید " + plan.title(), "renewal:" + month.getId(), null, null);
            SessionPackage next = newBundle(user.getId(), plan, month.getPrice(), month.getPromoTier(), true);
            // the next month starts where this one ends
            next.setExpiresAt(month.getExpiresAt().plus(Duration.ofDays(plan.validDays())));
            next.setPromoTier(null);
            packageRepository.save(next);
        }
    }

    private java.util.function.BiConsumer<User, String> renewalFailed;

    private java.util.function.BiConsumer<User, String> sessionSettled;

    private java.util.function.BiConsumer<User, String> freeUsed;

    /** Called when a free session is spent (its email never gets another). */
    public void onFreeUsed(java.util.function.BiConsumer<User, String> listener) {
        this.freeUsed = listener;
    }

    /** Called when a session with a conversation in it closes (for the referral reward). */
    public void onSessionSettled(java.util.function.BiConsumer<User, String> listener) {
        this.sessionSettled = listener;
    }

    public void onRenewalFailed(java.util.function.BiConsumer<User, String> listener) {
        this.renewalFailed = listener;
    }

    private java.util.function.BiConsumer<User, String> refundRecorded;

    /** The person is told that a bundle of theirs was cancelled and where the money goes. */
    public void onRefundRecorded(java.util.function.BiConsumer<User, String> listener) {
        this.refundRecorded = listener;
    }

    /** Refunds waiting for the admin, newest first. */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> pendingRefunds() {
        return packageRepository.findAllByRefundStatusOrderByIdDesc("PENDING").stream().map(item -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("userId", item.getUserId());
            row.put("user", userRepository.findById(item.getUserId()).map(User::visibleName).orElse(""));
            row.put("plan", titleOf(item.getTier()));
            row.put("paid", item.getPrice());
            row.put("usedSessions", item.getSessionsUsed());
            row.put("refund", item.getRefundAmount());
            row.put("cancelledAt", item.getCancelledAt() == null ? null : item.getCancelledAt().toEpochMilli());
            return row;
        }).toList();
    }

    /** The admin paid the refund back to the person's bank account. */
    @Transactional
    public void refundPaid(Long packageId) {
        SessionPackage bundle = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("این بسته پیدا نشد."));
        bundle.setRefundStatus("PAID");
        bundle.setRefundPaidAt(Instant.now());
        packageRepository.save(bundle);
    }

    private static String titleOf(String tier) {
        SessionPackageTier plan = SessionPackageTier.of(tier);
        return plan == null ? tier : plan.title();
    }

    /** A free session a running session will use is already spoken for, or one free session could open many rooms. */
    private int freeAvailable(User user, Long room) {
        if (user == null || user.getId() == null) {
            return freeLeft(user);
        }
        return (int) Math.max(0, freeLeft(user) - chatRoomRepository.countAwaitingFirstBillOf(user.getId(), room));
    }

    /** A confirmed email (also Google's) or an SMS-checked number; only then is there a free session. */
    public static boolean confirmed(User user) {
        return user != null && (user.isEmailVerified() || Boolean.TRUE.equals(user.getPhoneVerified()));
    }

    public int freeLeft(User user) {
        if (!confirmed(user)) {
            return 0;
        }
        int used = user == null || user.getFreeSessionsUsed() == null ? 0 : user.getFreeSessionsUsed();
        int gifted = user == null || user.getGiftedSessions() == null ? 0 : user.getGiftedSessions();
        return Math.max(0, freeSessions + gifted - used);
    }

    /**
     * Before a session opens (a new conversation, or «start» for the next one):
     * the creator has a free session left or credit for one. Otherwise the person
     * asking hears what to do: the creator to charge, the partner that the
     * creator has to.
     */
    @Transactional
    public void requireCanStart(ChatRoom room, User asking) {
        User owner = room == null || room.getCreatedBy() == null ? asking
                : userRepository.findById(room.getCreatedBy()).orElse(asking);
        requireCanStart(owner, asking, room == null ? NO_ROOM : room.getId());
    }

    @Transactional
    public void requireCanStart(User owner, User asking) {
        requireCanStart(owner, asking, NO_ROOM);
    }

    private void requireCanStart(User owner, User asking, Long room) {
        if (owner == null || freeAvailable(owner, room) > 0
                || !packageRepository.findUsable(owner.getId(), Instant.now()).isEmpty()
                || walletService.getOrCreate(owner).getBalance() >= sessionPrice) {
            return;
        }
        boolean mine = asking == null || Objects.equals(owner.getId(), asking.getId());
        String text = mine
                ? "جلسه‌های رایگانت تمام شده و اعتبار حسابت برای یک جلسهٔ دیگر کافی نیست. هزینهٔ هر جلسه "
                + toman(sessionPrice) + " است؛ از «اعتبار حساب» در داشبورد، حسابت را شارژ کن."
                : "برای شروع جلسهٔ بعد، " + owner.visibleName()
                + " باید اعتبار حسابش را شارژ کند؛ هزینهٔ جلسه‌ها با سازندهٔ گفتگوست.";
        throw new SessionOnBreakException(SessionOnBreakException.PAYMENT, text);
    }

    /**
     * Settles a session that closed with a conversation in it: a free one if the
     * creator has any left, otherwise the price from their wallet. UNPAID when
     * the wallet ran short meanwhile (it was checked when the session opened).
     */
    @Transactional
    public String settle(ChatRoom room, int sessionNumber) {
        User owner = room == null || room.getCreatedBy() == null ? null
                : userRepository.findById(room.getCreatedBy()).orElse(null);
        if (owner == null) {
            return UNPAID;
        }
        long paused = room.getId() == null ? 0 : aiHealth.pausedSeconds(room.getId(), sessionNumber);
        if (paused > 0 && paused >= outageFreeSeconds) {
            return OUTAGE;
        }
        if (sessionSettled != null) {
            sessionSettled.accept(owner, room.getTitle());
        }
        // a bought bundle first: the free session is the short first meeting for someone without one
        List<SessionPackage> bundles = packageRepository.findUsableForUpdate(owner.getId(), Instant.now());
        if (!bundles.isEmpty()) {
            SessionPackage bundle = bundles.get(0);
            bundle.setSessionsUsed(bundle.getSessionsUsed() + 1);
            packageRepository.save(bundle);
            return PACKAGE;
        }
        if (freeLeft(owner) > 0) {
            if (freeUsed != null) {
                freeUsed.accept(owner, "");
            }
            owner.setFreeSessionsUsed((owner.getFreeSessionsUsed() == null ? 0 : owner.getFreeSessionsUsed()) + 1);
            userRepository.save(owner);
            return FREE;
        }
        Wallet wallet = walletService.lockByUserId(owner.getId());
        if (wallet.getBalance() < sessionPrice) {
            log.warn("Session {} of room {} closed unpaid: the wallet of user {} is short", sessionNumber,
                    room.getId(), owner.getId());
            return UNPAID;
        }
        String title = room.getTitle() == null || room.getTitle().isBlank() ? "" : " «" + room.getTitle().trim() + "»";
        walletService.debit(owner, wallet, sessionPrice, WalletTransactionType.SESSION_FEE,
                "هزینهٔ جلسهٔ " + digits(String.valueOf(sessionNumber)) + " گفتگوی" + title,
                "session:" + room.getId() + ":" + sessionNumber, room.getId(), null);
        return PAID;
    }

    static String toman(long amount) {
        return digits(String.format("%,d", amount)).replace(',', '٬') + " تومان";
    }

    private static String digits(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }
}
