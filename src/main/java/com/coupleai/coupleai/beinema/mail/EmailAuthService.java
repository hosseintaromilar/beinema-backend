package com.coupleai.coupleai.beinema.mail;

import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.Entity.EmailCode;
import com.coupleai.coupleai.beinema.Entity.FreeSessionGrant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Repository.EmailCodeRepository;
import com.coupleai.coupleai.beinema.Repository.FreeSessionGrantRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Service.AuthService;
import com.coupleai.coupleai.beinema.Service.ReferralService;
import com.coupleai.coupleai.beinema.Service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Sign-up and sign-in with an email and a password, the address confirmed with a six-digit code
 * (docs: 03-email-login-spec-FA.md). The free session needs a confirmed address and is given once
 * per normalized address, also if the account is removed and made again. Answers never tell whether
 * an address has an account. Existing username accounts keep working and can add an email.
 */
@Service
public class EmailAuthService {

    private static final Logger log = LoggerFactory.getLogger(EmailAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    static final Duration CODE_LIFE = Duration.ofMinutes(10);
    static final int MAX_TRIES = 5;
    static final Duration RESEND_GAP = Duration.ofSeconds(60);
    static final int PER_HOUR = 5;
    static final int PER_DAY = 10;
    static final int SIGNUPS_PER_IP_PER_DAY = 5;

    public static final String NOT_READY = "ارسال ایمیل هنوز راه‌اندازی نشده؛ فعلاً با نام کاربری یا گوگل وارد شو.";
    private static final String SENT_IF_KNOWN = "اگر این ایمیل ثبت شده باشد، کد برایش فرستاده شد.";

    private final UserRepository userRepository;
    private final EmailCodeRepository codeRepository;
    private final FreeSessionGrantRepository grantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmtpMailer mailer;
    private final WalletService walletService;
    private final ReferralService referralService;
    private final AuthService authService;
    private final byte[] secret;

    @Value("${beinema.billing.free-sessions:1}")
    private int freeSessions;

    public EmailAuthService(UserRepository userRepository, EmailCodeRepository codeRepository,
                            FreeSessionGrantRepository grantRepository, PasswordEncoder passwordEncoder,
                            SmtpMailer mailer, WalletService walletService, ReferralService referralService,
                            AuthService authService, @Value("${jwt.secret}") String secret) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.grantRepository = grantRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.walletService = walletService;
        this.referralService = referralService;
        this.authService = authService;
        this.secret = ("email-code:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    public record Result(String status, String message, String email) {
    }

    public boolean available() {
        return mailer.configured();
    }

    // ------------------------------------------------------------------ sign-up

    /** Makes the account (not yet confirmed) and sends the code; a second sign-up of an unconfirmed address resends. */
    @Transactional
    public Result signup(String rawEmail, String password, String displayName, String referralCode, String ip) {
        requireMail();
        String email = requireAddress(rawEmail);
        String normalized = EmailAddresses.normalize(email);
        String pass = AuthService.requirePassword(password);
        String name = displayName == null ? "" : displayName.trim();
        if (name.length() < 2 || name.length() > 60) {
            throw new IllegalArgumentException("نامی را که می‌خواهی دیده شود بنویس (۲ تا ۶۰ نویسه).");
        }
        User existing = userRepository.findByEmailNormalized(normalized).orElse(null);
        if (existing != null && existing.isEmailVerified()) {
            throw new IllegalArgumentException("با این ایمیل قبلاً حساب ساخته شده؛ وارد شو یا رمزت را بازیابی کن.");
        }
        if (existing == null) {
            if (userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("با این ایمیل قبلاً حساب ساخته شده؛ وارد شو یا رمزت را بازیابی کن.");
            }
            if (ip != null && userRepository.countByCreatedAtAfterAndSignupIp(LocalDateTime.now().minusDays(1), ip) >= SIGNUPS_PER_IP_PER_DAY) {
                throw new IllegalArgumentException("از این اینترنت امروز چند حساب ساخته شده؛ فردا دوباره امتحان کن.");
            }
            existing = userRepository.save(User.builder()
                    .name(name)
                    .email(email)
                    .emailNormalized(normalized)
                    .contactEmail(email)
                    .password(passwordEncoder.encode(pass))
                    .passwordSet(true)
                    .status(UserStatus.ACTIVE)
                    .role(UserRole.USER)
                    .emailVerified(false)
                    .signupIp(ip)
                    .build());
            walletService.getOrCreate(existing);
            referralService.rewardNewUser(existing, referralCode);
        } else {
            existing.setName(name);
            existing.setPassword(passwordEncoder.encode(pass));
            existing.setContactEmail(email);
            userRepository.save(existing);
        }
        issue(normalized, email, existing, EmailCode.SIGNUP_VERIFY, ip);
        return new Result("CODE_SENT", "کد ۶ رقمی به " + email + " فرستاده شد. اگر نرسید، پوشهٔ اسپم را هم ببین.", email);
    }

    /** The code from the email: the account is confirmed, the free session given if this address never had one. */
    @Transactional
    public AuthResponse verifySignup(String rawEmail, String code) {
        String normalized = EmailAddresses.normalize(rawEmail);
        User user = userRepository.findByEmailNormalized(normalized)
                .orElseThrow(() -> new IllegalArgumentException("کد درست نیست یا منقضی شده؛ کد تازه بگیر."));
        consume(normalized, EmailCode.SIGNUP_VERIFY, code);
        confirm(user, normalized);
        return authService.signIn(user);
    }

    // ------------------------------------------------------------------ codes

    /** A new code for the same purpose (the old ones stop working); limited per address and time. */
    @Transactional
    public Result resend(String rawEmail, String purpose, String ip) {
        requireMail();
        String normalized = EmailAddresses.normalize(rawEmail);
        if (EmailCode.PASSWORD_RESET.equals(purpose)) {
            return forgot(rawEmail, ip);
        }
        User user = userRepository.findByEmailNormalized(normalized).orElse(null);
        if (user != null && !user.isEmailVerified()) {
            issue(normalized, EmailAddresses.clean(rawEmail), user, EmailCode.SIGNUP_VERIFY, ip);
        }
        return new Result("CODE_SENT", SENT_IF_KNOWN, EmailAddresses.clean(rawEmail));
    }

    // ------------------------------------------------------------------ forgotten password

    /** Always the same answer, so no one learns which addresses have an account. */
    @Transactional
    public Result forgot(String rawEmail, String ip) {
        requireMail();
        String normalized = EmailAddresses.normalize(rawEmail);
        verifiedAccount(rawEmail, normalized)
                .ifPresent(user -> {
                    try {
                        issue(normalized, user.getContactEmail() == null ? user.getEmail() : user.getContactEmail(),
                                user, EmailCode.PASSWORD_RESET, ip);
                    } catch (IllegalArgumentException tooSoon) {
                        // the same answer either way
                    }
                });
        return new Result("CODE_SENT", SENT_IF_KNOWN, EmailAddresses.clean(rawEmail));
    }

    @Transactional
    public AuthResponse reset(String rawEmail, String code, String newPassword) {
        String normalized = EmailAddresses.normalize(rawEmail);
        String pass = AuthService.requirePassword(newPassword);
        User user = verifiedAccount(rawEmail, normalized)
                .orElseThrow(() -> new IllegalArgumentException("کد درست نیست یا منقضی شده؛ کد تازه بگیر."));
        consume(normalized, EmailCode.PASSWORD_RESET, code);
        user.setPassword(passwordEncoder.encode(pass));
        user.setPasswordSet(true);
        if (user.getEmailNormalized() == null) {
            // a Google account with its first password: from now on it signs in by email too
            user.setEmailNormalized(normalized);
        }
        userRepository.save(user);
        notify(user, "رمز حساب بین‌ما تغییر کرد",
                "رمز حسابت همین الان تغییر کرد. اگر کار تو نبود، فوراً با پشتیبانی تماس بگیر.");
        return authService.signIn(user);
    }

    /**
     * The confirmed account behind an address: one made by email, or one made with Google (Google
     * has checked the address, but the account was never written down as an email account). Without
     * the second, a Google user who tries to sign up by email is told to reset the password, and the
     * reset never comes.
     */
    private java.util.Optional<User> verifiedAccount(String rawEmail, String normalized) {
        return userRepository.findByEmailNormalized(normalized)
                .or(() -> userRepository.findByEmail(EmailAddresses.clean(rawEmail))
                        .filter(user -> user.getGoogleSubject() != null && user.getEmailNormalized() == null
                                && !user.isDeleted()))
                .filter(User::isEmailVerified);
    }

    // ------------------------------------------------------------------ an existing account adds or changes its email

    /** A username account (or one changing its address) gets a code at the new address. */
    @Transactional
    public Result addEmail(User user, String rawEmail, String currentPassword, String ip) {
        requireMail();
        String email = requireAddress(rawEmail);
        String normalized = EmailAddresses.normalize(email);
        if (user.isEmailVerified() && user.getEmailNormalized() != null) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new IllegalArgumentException("برای عوض کردن ایمیل، رمز فعلی‌ات را درست بنویس.");
            }
        }
        userRepository.findByEmailNormalized(normalized)
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("این ایمیل به حساب دیگری وصل است. برای یکی کردن حساب‌ها به پشتیبانی پیام بده.");
                });
        String purpose = user.isEmailVerified() && user.getEmailNormalized() != null
                ? EmailCode.EMAIL_CHANGE : EmailCode.MIGRATION_VERIFY;
        issue(normalized, email, user, purpose, ip);
        return new Result("CODE_SENT", "کد ۶ رقمی به " + email + " فرستاده شد.", email);
    }

    @Transactional
    public Result verifyAddedEmail(User user, String rawEmail, String code) {
        String email = requireAddress(rawEmail);
        String normalized = EmailAddresses.normalize(email);
        boolean changing = user.isEmailVerified() && user.getEmailNormalized() != null;
        consume(normalized, changing ? EmailCode.EMAIL_CHANGE : EmailCode.MIGRATION_VERIFY, code);
        String old = user.getContactEmail();
        user.setContactEmail(email);
        if (changing) {
            // a new address never brings a second free session
            user.setEmailNormalized(normalized);
            userRepository.save(user);
            if (old != null) {
                send(old, "ایمیل حساب بین‌ما عوض شد",
                        "ایمیل حسابت در بین‌ما عوض شد. اگر کار تو نبود، فوراً با پشتیبانی تماس بگیر.");
            }
        } else {
            confirm(user, normalized);
        }
        return new Result("VERIFIED", "ایمیلت تأیید شد.", email);
    }

    // ------------------------------------------------------------------ inside

    /** Confirms the address and gives the free session only if this address never had one. */
    private void confirm(User user, String normalized) {
        user.setEmailNormalized(normalized);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        if (grantRepository.existsByEmailNormalized(normalized)) {
            int used = user.getFreeSessionsUsed() == null ? 0 : user.getFreeSessionsUsed();
            user.setFreeSessionsUsed(Math.max(used, freeSessions));
        }
        userRepository.save(user);
    }

    /** Called when a free session is used: that address never gets another one. */
    @Transactional
    public void freeSessionUsed(User user) {
        String normalized = user.getEmailNormalized() != null ? user.getEmailNormalized()
                : user.getEmail() != null && !user.getEmail().endsWith("@users.beinema") ? EmailAddresses.normalize(user.getEmail()) : null;
        if (normalized != null && !grantRepository.existsByEmailNormalized(normalized)) {
            FreeSessionGrant grant = new FreeSessionGrant();
            grant.setEmailNormalized(normalized);
            grant.setUserId(user.getId());
            grantRepository.save(grant);
        }
    }

    private void issue(String normalized, String sendTo, User user, String purpose, String ip) {
        LocalDateTime now = LocalDateTime.now();
        EmailCode last = codeRepository.findFirstByEmailNormalizedAndPurposeAndConsumedAtIsNullOrderByIdDesc(normalized, purpose).orElse(null);
        if (last != null && last.getCreatedAt() != null && last.getCreatedAt().isAfter(now.minus(RESEND_GAP))) {
            throw new IllegalArgumentException("یک دقیقه صبر کن، بعد کد تازه بگیر.");
        }
        if (codeRepository.countByEmailNormalizedAndCreatedAtAfter(normalized, now.minusHours(1)) >= PER_HOUR
                || codeRepository.countByEmailNormalizedAndCreatedAtAfter(normalized, now.minusDays(1)) >= PER_DAY) {
            throw new IllegalArgumentException("برای این ایمیل کد زیادی فرستاده شده؛ کمی بعد دوباره امتحان کن.");
        }
        if (ip != null && codeRepository.countByIpAndCreatedAtAfter(ip, now.minusHours(1)) >= 20) {
            throw new IllegalArgumentException("درخواست زیادی از این اینترنت آمده؛ کمی بعد دوباره امتحان کن.");
        }
        // a new code makes the older ones of the same purpose useless
        for (EmailCode older : codeRepository.findAllByEmailNormalizedAndPurposeAndConsumedAtIsNull(normalized, purpose)) {
            older.setConsumedAt(Instant.now());
            codeRepository.save(older);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailCode row = new EmailCode();
        row.setEmailNormalized(normalized);
        row.setUserId(user == null ? null : user.getId());
        row.setPurpose(purpose);
        row.setCodeHash(hash(normalized, purpose, code));
        row.setExpiresAt(Instant.now().plus(CODE_LIFE));
        row.setIp(ip);
        codeRepository.save(row);
        String name = user == null || user.getName() == null || user.getName().isBlank() ? "" : " " + user.getName();
        String persian = persian(code);
        switch (purpose) {
            case EmailCode.PASSWORD_RESET -> send(sendTo, "کد بازیابی رمز بین‌ما: " + code,
                    "سلام،\n\nبرای تغییر رمز حسابت در بین‌ما این کد را وارد کن: " + code + " (" + persian + ")\n"
                            + "کد تا ۱۰ دقیقه معتبر است. اگر درخواست نداده‌ای، کاری لازم نیست؛ رمزت عوض نمی‌شود.\n\n— تیم بین‌ما");
            case EmailCode.MIGRATION_VERIFY, EmailCode.EMAIL_CHANGE -> send(sendTo, "تأیید ایمیل حساب بین‌ما: " + code,
                    "سلام" + name + "،\n\nبرای امن‌تر شدن حسابت، از این به بعد ورود با ایمیل هم ممکن است. کد تأیید: "
                            + code + " (" + persian + ")\nاین کد ۱۰ دقیقه اعتبار دارد.\n\n— تیم بین‌ما");
            default -> send(sendTo, "کد تأیید بین‌ما: " + code,
                    "سلام" + name + "،\n\nبه بین‌ما خوش آمدی. کد تأیید ایمیلت:\n\n" + code + " (" + persian + ")\n\n"
                            + "این کد تا ۱۰ دقیقه معتبر است. اگر تو ثبت‌نام نکرده‌ای، این ایمیل را نادیده بگیر.\n\n— تیم بین‌ما");
        }
    }

    /** Checks a code: ten minutes, one use, five wrong tries and it is gone. */
    private void consume(String normalized, String purpose, String rawCode) {
        String code = latinDigits(rawCode == null ? "" : rawCode).replaceAll("\\D", "");
        EmailCode row = codeRepository.findFirstByEmailNormalizedAndPurposeAndConsumedAtIsNullOrderByIdDesc(normalized, purpose)
                .orElseThrow(() -> new IllegalArgumentException("کد درست نیست یا منقضی شده؛ کد تازه بگیر."));
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("این کد منقضی شده؛ کد تازه بگیر.");
        }
        boolean right = MessageDigest.isEqual(row.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(normalized, purpose, code).getBytes(StandardCharsets.US_ASCII));
        if (!right) {
            row.setAttempts(row.getAttempts() + 1);
            if (row.getAttempts() >= MAX_TRIES) {
                row.setConsumedAt(Instant.now());
            }
            codeRepository.save(row);
            throw new IllegalArgumentException(row.getAttempts() >= MAX_TRIES
                    ? "چند بار اشتباه شد؛ کد تازه بگیر." : "کد درست نیست.");
        }
        row.setConsumedAt(Instant.now());
        codeRepository.save(row);
    }

    private String hash(String normalized, String purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose + ":" + normalized + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void notify(User user, String subject, String body) {
        String to = user.getContactEmail();
        if (to != null && mailer.configured()) {
            try {
                send(to, subject, body);
            } catch (RuntimeException failure) {
                log.warn("Could not tell user {} by email: {}", user.getId(), failure.getMessage());
            }
        }
    }

    private void send(String to, String subject, String body) {
        try {
            mailer.send(to, subject, body);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("فرستادن ایمیل ممکن نشد. نشانی را بررسی کن یا کمی بعد دوباره امتحان کن.");
        }
    }

    private void requireMail() {
        if (!mailer.configured()) {
            throw new IllegalArgumentException(NOT_READY);
        }
    }

    private static String requireAddress(String raw) {
        if (!EmailAddresses.valid(raw)) {
            throw new IllegalArgumentException("نشانی ایمیل درست نیست.");
        }
        if (EmailAddresses.disposable(raw)) {
            throw new IllegalArgumentException("لطفاً با ایمیل شخصی ثابت ثبت‌نام کن؛ ایمیل‌های موقت پذیرفته نمی‌شوند.");
        }
        return EmailAddresses.clean(raw);
    }

    private static String latinDigits(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(c >= '۰' && c <= '۹' ? (char) ('0' + (c - '۰')) : c >= '٠' && c <= '٩' ? (char) ('0' + (c - '٠')) : c);
        }
        return out.toString();
    }

    private static String persian(String digits) {
        StringBuilder out = new StringBuilder(digits.length());
        for (char c : digits.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }

    /** An address not confirmed in 7 days is let go, so it can be used again. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Tehran")
    @Transactional
    public void releaseUnconfirmed() {
        for (User user : userRepository.findAllByEmailVerifiedFalseAndEmailNormalizedIsNotNullAndCreatedAtBefore(
                LocalDateTime.now().minusDays(7))) {
            boolean emailAccount = user.getUsername() == null && user.getPhoneNumber() == null && user.getGoogleSubject() == null;
            user.setEmailNormalized(null);
            if (emailAccount) {
                user.setDeleted(true);
                user.setStatus(UserStatus.INACTIVE);
                user.setEmail("released-" + user.getId() + "@users.beinema");
            }
            userRepository.save(user);
        }
    }

    static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
