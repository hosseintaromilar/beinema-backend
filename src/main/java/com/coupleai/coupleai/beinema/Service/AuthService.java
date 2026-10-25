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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final String PHONE_EMAIL_SUFFIX = "@users.beinema";

    private static final String PHONE_ONLY =
            "ثبت‌نام و ورود فقط با شماره موبایل انجام می‌شود.";

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


    public AuthResponse register(RegisterRequest request) {
        throw new InvalidCredentialsException(PHONE_ONLY);
    }


    public AuthResponse login(LoginRequest request) {
        throw new InvalidCredentialsException(PHONE_ONLY);
    }


    public LogoutResponse forgotPassword(ForgotPasswordRequest request) {
        throw new InvalidCredentialsException(PHONE_ONLY);
    }


    public LogoutResponse resetPassword(ResetPasswordRequest request) {
        throw new InvalidCredentialsException(PHONE_ONLY);
    }


    public OtpRequestResponse requestOtp(OtpPhoneRequest request) {
        String phone = PhoneNumbers.normalize(
                request == null ? null : request.getPhoneNumber()
        );

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
        String phone = PhoneNumbers.normalize(
                request == null ? null : request.getPhoneNumber()
        );
        consumeLoginCode(phone, request == null ? null : request.getCode());
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
     * Sign-in with a Google ID token. A new Google user gets an account without
     * a phone number; the app then asks for the number once (requestPhoneLink /
     * verifyPhoneLink), because partners and invitations are matched by phone.
     */
    public AuthResponse signInWithGoogle(GoogleSignInRequest request) {
        GoogleIdentity identity = googleTokenVerifier.verify(
                request == null ? null : request.getCredential()
        );

        User user = userRepository.findByGoogleSubject(identity.subject())
                .or(() -> userRepository.findByEmail(identity.email())
                        .filter(existing -> existing.getGoogleSubject() == null
                                && !existing.isDeleted()))
                .orElseGet(() -> createGoogleUser(identity));

        if (!user.isActive()) {
            throw new UserNotActiveException("حساب کاربری شما فعال نیست");
        }
        if (user.getGoogleSubject() == null) {
            user.setGoogleSubject(identity.subject());
        }
        if (user.getPhoneNumber() != null && isListedPhone(adminPhones, user.getPhoneNumber())) {
            user.setRole(UserRole.ADMIN);
        }
        user.updateLastLogin();
        userRepository.save(user);
        return sessionFor(user);
    }


    public OtpRequestResponse requestPhoneLink(OtpPhoneRequest request) {
        User user = requireUserWithoutPhone();
        String phone = PhoneNumbers.normalize(
                request == null ? null : request.getPhoneNumber()
        );

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
        User user = requireUserWithoutPhone();
        String phone = PhoneNumbers.normalize(
                request == null ? null : request.getPhoneNumber()
        );
        consumeLoginCode(phone, request == null ? null : request.getCode());
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


    private User createGoogleUser(GoogleIdentity identity) {
        if (userRepository.existsByEmail(identity.email())) {
            throw new IllegalArgumentException(
                    "این ایمیل به حساب دیگری وصل است. با شماره موبایل وارد شوید."
            );
        }

        String firstName = namePart(identity.givenName());
        String lastName = namePart(identity.familyName());
        boolean named = firstName != null && lastName != null;

        // no wallet or referral reward yet: those wait for the verified phone number
        return userRepository.save(User.builder()
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


    private AuthResponse openSession(User user, String phone) {
        if (isListedPhone(adminPhones, phone)) {
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
                user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()
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
