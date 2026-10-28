package com.coupleai.coupleai.beinema.Service;


import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.ForgotPasswordRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.GoogleConfigResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.GoogleSignInRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.LoginRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.LogoutResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpPhoneRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpRequestResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpVerifyRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.RegisterRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.ResetPasswordRequest;
import com.coupleai.coupleai.beinema.Entity.PhoneLoginCode;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import com.coupleai.coupleai.beinema.Exception.SmsDeliveryException;
import com.coupleai.coupleai.beinema.Exception.UserNotActiveException;
import com.coupleai.coupleai.beinema.Repository.PhoneLoginCodeRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Security.GoogleTokenVerifier;
import com.coupleai.coupleai.beinema.Security.GoogleTokenVerifier.GoogleIdentity;
import com.coupleai.coupleai.beinema.Security.JwtService;
import com.coupleai.coupleai.beinema.Security.TokenBlacklistService;
import com.coupleai.coupleai.beinema.Service.sms.SmsProvider;
import com.coupleai.coupleai.beinema.Util.PhoneNumbers;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;


@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final String PHONE_EMAIL_SUFFIX = "@users.beinema";

    private static final String SMS_OFF =
            "ورود با کد پیامکی فعلاً فعال نیست؛ با شماره موبایل و رمز وارد شو یا ثبت‌نام کن.";

    private static final String NO_RECOVERY =
            "بازیابی رمز فعلاً ممکن نیست؛ اگر حسابت به گوگل وصل است، با گوگل وارد شو.";

    private static final String WRONG_LOGIN = "شماره موبایل یا رمز درست نیست.";

    static final int MIN_PASSWORD = 8;

    private static final int MAX_PASSWORD = 72;

    /** Wrong passwords for one number in a quarter of an hour before it waits. */
    private static final int LOGIN_MAX_FAILURES = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int OTP_TTL_MINUTES = 2;

    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private static final int HOURLY_LIMIT = 5;

    private final UserRepository userRepository;

    private final PhoneLoginCodeRepository phoneLoginCodeRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final TokenBlacklistService tokenBlacklistService;

    private final SmsProvider smsProvider;

    private final WalletService walletService;

    private final PhoneOtpLockService phoneOtpLockService;

    private final ReferralService referralService;

    private final GoogleTokenVerifier googleTokenVerifier;

    private final CurrentUserService currentUserService;

    @Value("${beinema.admin-phones:}")
    private String adminPhones;

    @Value("${beinema.otp-bypass-phones:}")
    private String otpBypassPhones;

    /**
     * With a code, the bypass numbers get no SMS but must type this fixed code;
     * without one (local development) they sign in directly.
     */
    @Value("${beinema.otp-bypass-code:}")
    private String otpBypassCode;

    private static final int BYPASS_MAX_FAILURES = 5;

    /** Wrong fixed codes per phone in the last hour. */
    private final Map<String, Deque<Instant>> bypassFailures = new ConcurrentHashMap<>();

    /** Wrong passwords per phone in the last quarter of an hour. */
    private final Map<String, Deque<Instant>> loginFailures = new ConcurrentHashMap<>();

    /**
     * Signing in with an SMS code. Off for now: people sign up with the number
     * and a password (or Google), and the number is written down without a code.
     */
    @Value("${beinema.auth.sms-login:false}")
    private boolean smsLogin;


    /**
     * Sign-up with the mobile number and a password. The number is written down,
     * not confirmed (no SMS for now); the name is asked in the next step.
     */
    public AuthResponse register(RegisterRequest request) {
        String phone = PhoneNumbers.normalize(request == null ? null : request.getPhoneNumber());
        String password = requirePassword(request == null ? null : request.getPassword());
        User existing = userRepository.findByPhoneNumber(phone).orElse(null);
        if (existing != null && claimsOldSmsAccount(existing)) {
            // an account made by SMS code before passwords: its number gets the password once
            existing.setPassword(passwordEncoder.encode(password));
            existing.setPasswordSet(true);
            return openSession(userRepository.save(existing), phone);
        }
        if (existing != null || userRepository.existsByEmail(phone + PHONE_EMAIL_SUFFIX)) {
            throw new IllegalArgumentException("با این شماره قبلاً حساب ساخته شده؛ وارد شو.");
        }

        User saved = userRepository.save(User.builder()
                .name("")
                .email(phone + PHONE_EMAIL_SUFFIX)
                .phoneNumber(phone)
                .phoneVerified(false)
                .password(passwordEncoder.encode(password))
                .passwordSet(true)
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .emailVerified(false)
                .build());
        walletService.getOrCreate(saved);
        referralService.rewardNewUser(saved, request.getReferralCode());
        return openSession(saved, phone);
    }


    /** Sign-in with the mobile number and the password; a few wrong tries make the number wait. */
    public AuthResponse login(LoginRequest request) {
        String phone = PhoneNumbers.normalize(request == null ? null : request.getPhoneNumber());
        String password = request == null || request.getPassword() == null ? "" : request.getPassword();
        Deque<Instant> failures = loginFailures.computeIfAbsent(phone, key -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        failures.removeIf(at -> at.isBefore(now.minus(Duration.ofMinutes(15))));
        if (failures.size() >= LOGIN_MAX_FAILURES) {
            throw new InvalidCredentialsException("چند بار رمز اشتباه وارد شد؛ ربع ساعت دیگر دوباره امتحان کن.");
        }

        User user = userRepository.findByPhoneNumber(phone).orElse(null);
        if (user == null || user.isDeleted() || !passwordEncoder.matches(password, user.getPassword())) {
            failures.add(now);
            throw new InvalidCredentialsException(WRONG_LOGIN);
        }
        if (!user.isActive()) {
            throw new UserNotActiveException("حساب کاربری شما فعال نیست");
        }
        failures.clear();
        return openSession(user, phone);
    }


    public LogoutResponse forgotPassword(ForgotPasswordRequest request) {
        throw new InvalidCredentialsException(NO_RECOVERY);
    }


    public LogoutResponse resetPassword(ResetPasswordRequest request) {
        throw new InvalidCredentialsException(NO_RECOVERY);
    }


    /**
     * The few accounts made by SMS code before sign-up had passwords: their
     * number was confirmed by a code, they have no password of their own and
     * no Google login (a Google account's number is only written down, so it
     * can never be claimed this way). Once claimed, passwordSet closes it.
     */
    private static boolean claimsOldSmsAccount(User user) {
        return !Boolean.TRUE.equals(user.getPasswordSet())
                && user.getGoogleSubject() == null
                && !user.isDeleted()
                && user.getEmail() != null
                && user.getEmail().endsWith(PHONE_EMAIL_SUFFIX);
    }


    static String requirePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("رمز باید دست‌کم ۸ نویسه باشد.");
        }
        if (password.length() > MAX_PASSWORD) {
            throw new IllegalArgumentException("رمز خیلی طولانی است؛ حداکثر ۷۲ نویسه.");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("رمز نمی‌تواند فقط فاصله باشد.");
        }
        return password;
    }


    private void requireSmsLogin() {
        if (!smsLogin) {
            throw new InvalidCredentialsException(SMS_OFF);
        }
    }


    public OtpRequestResponse requestOtp(OtpPhoneRequest request) {
        requireSmsLogin();
        String phone = PhoneNumbers.normalize(
                request == null ? null : request.getPhoneNumber()
        );

        if (fixedCodeFor(phone)) {
            return new OtpRequestResponse(FIXED_CODE_PROMPT, false, null, false, null);
        }

        if (isListedPhone(otpBypassPhones, phone)) {
            AuthResponse session = openPhoneSession(
                    phone,
                    request == null ? null : request.getReferralCode()
            );
            log.info("OTP bypass login for ***{}", phone.substring(phone.length() - 4));
            return new OtpRequestResponse(
                    "ورود انجام شد",
                    true,
                    session.getToken(),
                    session.isProfileComplete(),
                    session.getRole()
            );
        }

        sendLoginCode(phone);

        return new OtpRequestResponse(
                "کد ۴ رقمی به شماره موبایل شما پیامک شد.",
                false,
                null,
                false,
                null
        );
    }


    public AuthResponse verifyOtp(OtpVerifyRequest request) {
        requireSmsLogin();
        String phone = PhoneNumbers.normalize(
                request == null ? null : request.getPhoneNumber()
        );
        checkCode(phone, request == null ? null : request.getCode());
        return openPhoneSession(
                phone,
                request == null ? null : request.getReferralCode()
        );
    }


    public GoogleConfigResponse googleConfig() {
        return new GoogleConfigResponse(
                googleTokenVerifier.isEnabled(),
                googleTokenVerifier.isEnabled() ? googleTokenVerifier.clientId() : null
        );
    }


    /**
     * Sign-in with a Google ID token: one way in, like the phone number. A new
     * Google user gets a complete account (wallet, referral reward) without a
     * phone number; an invitation gives them the invited number when they accept it.
     */
    public AuthResponse signInWithGoogle(GoogleSignInRequest request) {
        GoogleIdentity identity = googleTokenVerifier.verify(
                request == null ? null : request.getCredential()
        );

        User user = userRepository.findByGoogleSubject(identity.subject())
                .or(() -> userRepository.findByEmail(identity.email())
                        .filter(existing -> existing.getGoogleSubject() == null
                                && !existing.isDeleted()))
                .orElseGet(() -> createGoogleUser(identity, request.getReferralCode()));

        if (!user.isActive()) {
            throw new UserNotActiveException("حساب کاربری شما فعال نیست");
        }
        if (user.getGoogleSubject() == null) {
            user.setGoogleSubject(identity.subject());
        }
        if (adminByPhone(user, user.getPhoneNumber())) {
            user.setRole(UserRole.ADMIN);
        }
        user.updateLastLogin();
        userRepository.save(user);
        return sessionFor(user);
    }


    public OtpRequestResponse requestPhoneLink(OtpPhoneRequest request) {
        requireSmsLogin();
        User user = requireUserWithoutPhone();
        String phone = PhoneNumbers.normalize(
                request == null ? null : request.getPhoneNumber()
        );

        if (fixedCodeFor(phone)) {
            return new OtpRequestResponse(FIXED_CODE_PROMPT, false, null, false, null);
        }

        if (isListedPhone(otpBypassPhones, phone)) {
            AuthResponse session = linkPhone(
                    user,
                    phone,
                    request == null ? null : request.getReferralCode()
            );
            return new OtpRequestResponse(
                    "شماره موبایل تأیید شد",
                    true,
                    session.getToken(),
                    session.isProfileComplete(),
                    session.getRole()
            );
        }

        sendLoginCode(phone);
        return new OtpRequestResponse(
                "کد ۴ رقمی به شماره موبایل شما پیامک شد.",
                false,
                null,
                false,
                null
        );
    }


    public AuthResponse verifyPhoneLink(OtpVerifyRequest request) {
        requireSmsLogin();
        User user = requireUserWithoutPhone();
        String phone = PhoneNumbers.normalize(
                request == null ? null : request.getPhoneNumber()
        );
        checkCode(phone, request == null ? null : request.getCode());
        return linkPhone(
                user,
                phone,
                request == null ? null : request.getReferralCode()
        );
    }


    public LogoutResponse logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            throw new InvalidCredentialsException(
                    "برای خروج باید وارد حساب شده باشید"
            );
        }

        tokenBlacklistService.revoke(token);
        return new LogoutResponse("خروج با موفقیت انجام شد");
    }


    private User createPhoneUser(String phone) {
        String email = phone + PHONE_EMAIL_SUFFIX;
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "ساخت حساب با این شماره ممکن نیست."
            );
        }

        User user = User.builder()
                .name("")
                .email(email)
                .phoneNumber(phone)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .emailVerified(false)
                .build();

        User saved = userRepository.save(user);
        walletService.getOrCreate(saved);
        return saved;
    }


    private AuthResponse openPhoneSession(String phone, String referralCode) {
        boolean[] created = {false};
        User user = userRepository.findByPhoneNumber(phone)
                .map(existing -> {
                    if (!existing.isActive()) {
                        throw new UserNotActiveException("حساب کاربری شما فعال نیست");
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    created[0] = true;
                    return createPhoneUser(phone);
                });

        if (created[0]) {
            referralService.rewardNewUser(user, referralCode);
        }

        return openSession(user, phone);
    }


    private void sendLoginCode(String phone) {
        LocalDateTime now = LocalDateTime.now();
        phoneLoginCodeRepository
                .findTopByPhoneNumberOrderByCreatedAtDesc(phone)
                .filter(latest -> !latest.isUsed()
                        && latest.getCreatedAt() != null
                        && latest.getCreatedAt().isAfter(now.minusSeconds(RESEND_COOLDOWN_SECONDS)))
                .ifPresent(latest -> {
                    throw new IllegalArgumentException(
                            "کد قبلی هنوز معتبر است. یک دقیقه دیگر دوباره تلاش کنید."
                    );
                });

        long sentRecently = phoneLoginCodeRepository.countByPhoneNumberAndCreatedAtAfter(
                phone,
                now.minusHours(1)
        );
        if (sentRecently >= HOURLY_LIMIT) {
            throw new IllegalArgumentException(
                    "تعداد درخواست کد بیش از حد مجاز است. کمی بعد دوباره تلاش کنید."
            );
        }

        phoneLoginCodeRepository.findByPhoneNumberAndUsedFalse(phone)
                .forEach(previous -> {
                    previous.setUsed(true);
                    phoneLoginCodeRepository.save(previous);
                });

        String code = String.format("%04d", RANDOM.nextInt(10_000));
        phoneLoginCodeRepository.save(
                PhoneLoginCode.builder()
                        .phoneNumber(phone)
                        .codeHash(passwordEncoder.encode(code))
                        .expiresAt(now.plusMinutes(OTP_TTL_MINUTES))
                        .attempts(0)
                        .used(false)
                        .build()
        );

        try {
            smsProvider.send(
                    phone,
                    "کد ورود بین‌ما: " + code + "\nاین کد تا ۲ دقیقه معتبر است."
            );
        } catch (SmsDeliveryException exception) {
            log.warn(
                    "OTP sms failed for ***{}: {}",
                    phone.substring(phone.length() - 4),
                    exception.getMessage()
            );
            throw new SmsDeliveryException(
                    "ارسال پیامک ناموفق بود. کمی بعد دوباره تلاش کنید.",
                    exception
            );
        }
    }


    private static final String FIXED_CODE_PROMPT =
            "برای این شماره پیامک فرستاده نمی‌شود؛ کد ثابت ورود را وارد کنید.";

    private boolean fixedCodeFor(String phone) {
        return otpBypassCode != null && !otpBypassCode.isBlank() && isListedPhone(otpBypassPhones, phone);
    }

    /** The fixed code for the bypass numbers, the SMS code for everyone else. */
    private void checkCode(String phone, String rawCode) {
        if (!fixedCodeFor(phone)) {
            consumeLoginCode(phone, rawCode);
            return;
        }
        Deque<Instant> failures = bypassFailures.computeIfAbsent(phone, key -> new ConcurrentLinkedDeque<>());
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
        failures.removeIf(at -> at.isBefore(hourAgo));
        if (failures.size() >= BYPASS_MAX_FAILURES) {
            throw new InvalidCredentialsException("تعداد تلاش‌ها بیش از حد مجاز است. یک ساعت دیگر دوباره تلاش کنید.");
        }
        String code = rawCode == null ? "" : rawCode.trim();
        boolean same = MessageDigest.isEqual(
                code.getBytes(StandardCharsets.UTF_8),
                otpBypassCode.trim().getBytes(StandardCharsets.UTF_8)
        );
        if (!same) {
            failures.add(Instant.now());
            log.warn("Wrong fixed login code for ***{}", phone.substring(phone.length() - 4));
            throw new InvalidCredentialsException("کد واردشده درست نیست.");
        }
        failures.clear();
    }


    private void consumeLoginCode(String phone, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{4}")) {
            throw new InvalidCredentialsException("کد ۴ رقمی پیامک‌شده را وارد کنید.");
        }

        PhoneLoginCode stored = phoneLoginCodeRepository
                .findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "کد ورود پیدا نشد. دوباره درخواست کنید."
                ));

        if (stored.getExpiresAt() == null
                || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setUsed(true);
            phoneLoginCodeRepository.save(stored);
            throw new InvalidCredentialsException(
                    "مهلت این کد تمام شده. دوباره کد بگیرید."
            );
        }

        if (!passwordEncoder.matches(code, stored.getCodeHash())) {
            int attempts = phoneOtpLockService.recordFailedAttempt(stored.getId());
            if (attempts >= PhoneOtpLockService.MAX_ATTEMPTS) {
                throw new InvalidCredentialsException(
                        "تعداد تلاش‌ها بیش از حد مجاز است. کد جدید بگیرید."
                );
            }
            throw new InvalidCredentialsException("کد واردشده درست نیست.");
        }

        stored.setUsed(true);
        phoneLoginCodeRepository.save(stored);
    }


    private User createGoogleUser(GoogleIdentity identity, String referralCode) {
        if (userRepository.existsByEmail(identity.email())) {
            throw new IllegalArgumentException(
                    "این ایمیل به حساب دیگری وصل است. با شماره موبایل وارد شوید."
            );
        }

        String firstName = namePart(identity.givenName());
        String lastName = namePart(identity.familyName());
        boolean named = firstName != null && lastName != null;

        User saved = userRepository.save(User.builder()
                .name(named ? firstName + " " + lastName : "")
                .firstName(named ? firstName : null)
                .lastName(named ? lastName : null)
                .email(identity.email())
                .googleSubject(identity.subject())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .emailVerified(true)
                .build());
        walletService.getOrCreate(saved);
        referralService.rewardNewUser(saved, referralCode);
        return saved;
    }


    private static String namePart(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.length() < 2 || trimmed.length() > 50 ? null : trimmed;
    }


    private User requireUserWithoutPhone() {
        User user = currentUserService.getCurrentUser();
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("شماره موبایل این حساب قبلاً تأیید شده است.");
        }
        return user;
    }


    /**
     * The phone is verified. If no account has it, it becomes this account's
     * number. If one does, that account is the person's real one: the Google
     * login moves to it and the empty account made by the Google sign-in is
     * retired (it could not be used before this step).
     */
    private AuthResponse linkPhone(User user, String phone, String referralCode) {
        User owner = userRepository.findByPhoneNumber(phone).orElse(null);

        if (owner == null) {
            user.setPhoneNumber(phone);
            User saved = userRepository.save(user);
            walletService.getOrCreate(saved);
            referralService.rewardNewUser(saved, referralCode);
            return openSession(saved, phone);
        }

        if (!owner.isActive()) {
            throw new UserNotActiveException("حساب کاربری این شماره فعال نیست");
        }
        String subject = user.getGoogleSubject();
        if (subject == null
                || (owner.getGoogleSubject() != null && !owner.getGoogleSubject().equals(subject))) {
            throw new IllegalArgumentException(
                    "این شماره به حساب دیگری وصل است."
            );
        }

        user.setGoogleSubject(null);
        user.setDeleted(true);
        userRepository.saveAndFlush(user);

        owner.setGoogleSubject(subject);
        if (!owner.hasRegisteredName() && user.hasRegisteredName()) {
            owner.setFirstName(user.getFirstName());
            owner.setLastName(user.getLastName());
            owner.setName(user.getName());
        }
        return openSession(owner, phone);
    }


    /**
     * An admin number makes the account an admin only when the number was
     * confirmed by an SMS code. A number typed in at sign-up or in the profile
     * (phoneVerified false, while SMS is off) proves nothing, so anyone could
     * otherwise become an admin by typing an admin's number.
     */
    private boolean adminByPhone(User user, String phone) {
        return phone != null
                && isListedPhone(adminPhones, phone)
                && !Boolean.FALSE.equals(user.getPhoneVerified());
    }


    private AuthResponse openSession(User user, String phone) {
        if (adminByPhone(user, phone)) {
            user.setRole(UserRole.ADMIN);
        }
        user.updateLastLogin();
        userRepository.save(user);
        return sessionFor(user);
    }


    private AuthResponse sessionFor(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(
                "ورود با موفقیت انجام شد",
                token,
                user.hasRegisteredName(),
                user.getRole().name(),
                // a Google account is complete without a phone number
                false
        );
    }


    private boolean isListedPhone(String list, String phone) {
        if (list == null || list.isBlank() || phone == null) {
            return false;
        }
        for (String item : list.split(",")) {
            String candidate = item.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (phone.equals(PhoneNumbers.normalize(candidate))) {
                return true;
            }
        }
        return false;
    }


    private static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

}
