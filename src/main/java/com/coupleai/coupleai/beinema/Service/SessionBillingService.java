package com.coupleai.coupleai.beinema.Service;

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
 * sessions are free (two, across all their conversations); then a session
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

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final SessionPackageRepository packageRepository;

    @Value("${beinema.billing.free-sessions:2}")
    private int freeSessions;

    @Value("${beinema.billing.session-price:100000}")
    private long sessionPrice;

    /** The usual price, shown struck through while the launch price is on. */
    @Value("${beinema.billing.session-list-price:500000}")
    private long sessionListPrice;

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
                        List<PackageOffer> packages, List<OwnedPackage> myPackages) {
    }

    /** A bundle on sale; listPrice is its sessions at the usual single price, struck through. */
    public record PackageOffer(String tier, String title, int sessions, long price, long listPrice,
                               long perSession, int validDays) {
    }

    /** A bundle someone bought that still has sessions and time left. */
    public record OwnedPackage(Long id, String tier, String title, int sessionsLeft, int sessionsTotal, long expiresAt) {
    }

    /** The public part, for the counselor pages: free sessions, the price and the bundles. */
    public record Prices(int freeSessions, long sessionPrice, long sessionListPrice, List<PackageOffer> packages) {
    }

    public Prices prices() {
        return new Prices(freeSessions, sessionPrice, sessionListPrice, packageOffers());
    }

    public List<PackageOffer> packageOffers() {
        return Arrays.stream(SessionPackageTier.values())
                .map(tier -> new PackageOffer(tier.name(), tier.title(), tier.sessions(), tier.price(),
                        tier.sessions() * sessionListPrice, tier.price() / tier.sessions(), SessionPackageTier.VALID_DAYS))
                .toList();
    }

    @Transactional
    public Offer offerFor(User user) {
        int left = freeLeft(user);
        long balance = walletService.getOrCreate(user).getBalance();
        List<OwnedPackage> mine = packageRepository.findUsable(user.getId(), Instant.now()).stream()
                .map(item -> new OwnedPackage(item.getId(), item.getTier(), titleOf(item.getTier()),
                        item.sessionsLeft(), item.getSessionsTotal(), item.getExpiresAt().toEpochMilli()))
                .toList();
        return new Offer(freeSessions, left, sessionPrice, sessionListPrice, balance,
                left > 0, left > 0 || !mine.isEmpty() || balance >= sessionPrice, cardNumber, cardHolder, bankName,
                packageOffers(), mine);
    }

    /** Buys a bundle with the wallet's credit; it can be used for a month. */
    @Transactional
    public OwnedPackage buyPackage(User user, String tierName) {
        SessionPackageTier tier;
        try {
            tier = SessionPackageTier.valueOf(String.valueOf(tierName).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("این بسته پیدا نشد.");
        }
        Wallet wallet = walletService.lockByUserId(user.getId());
        if (wallet.getBalance() < tier.price()) {
            throw new IllegalArgumentException("اعتبار حسابت برای بستهٔ " + tier.title() + " کافی نیست؛ "
                    + toman(tier.price()) + " لازم است. اول حسابت را با کارت به کارت شارژ کن.");
        }
        walletService.debit(user, wallet, tier.price(), WalletTransactionType.PACKAGE_PURCHASE,
                "خرید بستهٔ " + tier.title() + " (" + digits(String.valueOf(tier.sessions())) + " جلسه)",
                "package:" + user.getId() + ":" + System.currentTimeMillis(), null, null);

        SessionPackage bought = new SessionPackage();
        bought.setUserId(user.getId());
        bought.setTier(tier.name());
        bought.setSessionsTotal(tier.sessions());
        bought.setSessionsUsed(0);
        bought.setPrice(tier.price());
        bought.setExpiresAt(Instant.now().plus(Duration.ofDays(SessionPackageTier.VALID_DAYS)));
        SessionPackage saved = packageRepository.save(bought);
        return new OwnedPackage(saved.getId(), saved.getTier(), tier.title(), saved.sessionsLeft(),
                saved.getSessionsTotal(), saved.getExpiresAt().toEpochMilli());
    }

    private static String titleOf(String tier) {
        try {
            return SessionPackageTier.valueOf(tier).title();
        } catch (IllegalArgumentException unknown) {
            return tier;
        }
    }

    public int freeLeft(User user) {
        int used = user == null || user.getFreeSessionsUsed() == null ? 0 : user.getFreeSessionsUsed();
        return Math.max(0, freeSessions - used);
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
        requireCanStart(owner, asking);
    }

    @Transactional
    public void requireCanStart(User owner, User asking) {
        if (owner == null || freeLeft(owner) > 0
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
        if (freeLeft(owner) > 0) {
            owner.setFreeSessionsUsed((owner.getFreeSessionsUsed() == null ? 0 : owner.getFreeSessionsUsed()) + 1);
            userRepository.save(owner);
            return FREE;
        }
        // then a bought bundle, the one that ends first
        List<SessionPackage> bundles = packageRepository.findUsableForUpdate(owner.getId(), Instant.now());
        if (!bundles.isEmpty()) {
            SessionPackage bundle = bundles.get(0);
            bundle.setSessionsUsed(bundle.getSessionsUsed() + 1);
            packageRepository.save(bundle);
            return PACKAGE;
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
