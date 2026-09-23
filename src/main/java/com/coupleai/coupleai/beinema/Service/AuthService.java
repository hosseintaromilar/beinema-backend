package com.coupleai.coupleai.beinema.Service;


import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.ForgotPasswordRequest;
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
            AuthResponse session = openPhoneSession(phone);
            log.info("OTP bypass login for ***{}", phone.substring(phone.length() - 4));
            return new OtpRequestResponse(
                    "ورود انجام شد",
                    true,
                    session.getToken(),
                    session.isProfileComplete(),
                    session.getRole()
            );
        }

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
        String code = request == null || request.getCode() == null
                ? ""
                : request.getCode().trim();

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
        return openPhoneSession(phone);
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


    private AuthResponse openPhoneSession(String phone) {
        User user = userRepository.findByPhoneNumber(phone)
                .map(existing -> {
                    if (!existing.isActive()) {
                        throw new UserNotActiveException("حساب کاربری شما فعال نیست");
                    }
                    return existing;
                })
                .orElseGet(() -> createPhoneUser(phone));

        if (isListedPhone(adminPhones, phone)) {
            user.setRole(UserRole.ADMIN);
        }
        user.updateLastLogin();
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(
                "ورود با موفقیت انجام شد",
                token,
                user.hasRegisteredName(),
                user.getRole().name()
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
