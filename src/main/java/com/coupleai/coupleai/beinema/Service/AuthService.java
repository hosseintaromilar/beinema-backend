package com.coupleai.coupleai.beinema.Service;


import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.ForgotPasswordRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.LoginRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.LogoutResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.RegisterRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.ResetPasswordRequest;
import com.coupleai.coupleai.beinema.Entity.PasswordResetCode;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Exception.EmailAlreadyExistsException;
import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import com.coupleai.coupleai.beinema.Exception.UserNotActiveException;
import com.coupleai.coupleai.beinema.Repository.PasswordResetCodeRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.JwtService;
import com.coupleai.coupleai.beinema.Security.TokenBlacklistService;
import com.coupleai.coupleai.beinema.Service.sms.SmsProvider;
import com.coupleai.coupleai.beinema.Util.PhoneNumbers;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;


@Service

@RequiredArgsConstructor

@Transactional

public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String RESET_NOTICE =
            "اگر این ایمیل در بین‌ما ثبت شده باشد، کد بازیابی برایتان ارسال می‌شود.";


    private final UserRepository userRepository;


    private final PasswordEncoder passwordEncoder;


    private final JwtService jwtService;


    private final TokenBlacklistService tokenBlacklistService;

    private final PasswordResetCodeRepository passwordResetCodeRepository;

    private final SmsProvider smsProvider;



    public AuthResponse register(

            RegisterRequest request

    ) {


        String email = normalizeEmail(

                request.getEmail()

        );



        if (

                userRepository.existsByEmail(email)

        ) {

            throw new EmailAlreadyExistsException(

                    "این ایمیل قبلاً ثبت ‌نام کرده است"

            );

        }


        String phoneNumber = null;

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {

            phoneNumber = PhoneNumbers.normalize(request.getPhoneNumber());

            if (userRepository.existsByPhoneNumber(phoneNumber)) {

                throw new EmailAlreadyExistsException(
                        "این شماره موبایل قبلاً ثبت‌نام کرده است"
                );
            }
        }



        User user = User.builder()

                .name(request.getName())

                .email(email)

                .phoneNumber(phoneNumber)

                .password(

                        passwordEncoder.encode(

                                request.getPassword()

                        )

                )

                .status(UserStatus.ACTIVE)

                .role(UserRole.USER)

                .emailVerified(false)

                .build();



        User savedUser = userRepository.save(

                user

        );



        String token = jwtService.generateToken(

                savedUser.getId(),

                savedUser.getEmail()

        );



        return new AuthResponse(

                "ثبت‌نام با موفقیت انجام شد",

                token

        );

    }



    public AuthResponse login(

            LoginRequest request

    ) {


        String email = normalizeEmail(

                request.getEmail()

        );



        User user = userRepository

                .findByEmail(email)

                .orElseThrow(() ->

                        new InvalidCredentialsException(

                                "کاربری بااین ایمیل پیدا نشد"

                        )

                );



        if (

                !user.isActive()

        ) {

            throw new UserNotActiveException(

                    "حساب کاربری شما فعال نیست"

            );

        }



        if (

                !passwordEncoder.matches(

                        request.getPassword(),

                        user.getPassword()

                )

        ) {

            throw new InvalidCredentialsException(

                    "ایمیل یا رمز عبور اشتباه است"

            );

        }



        user.updateLastLogin();



        String token = jwtService.generateToken(

                user.getId(),

                user.getEmail()

        );



        return new AuthResponse(

                "ورود با موفقیت انجام شد",

                token

        );

    }


    public LogoutResponse logout(

            String authorizationHeader

    ) {

        String token = extractBearerToken(authorizationHeader);

        if (token == null) {
            throw new InvalidCredentialsException(
                    "برای خروج باید وارد حساب شده باشید"
            );
        }

        tokenBlacklistService.revoke(token);

        return new LogoutResponse(
                "خروج با موفقیت انجام شد"
        );

    }


    public LogoutResponse forgotPassword(ForgotPasswordRequest request) {

        String email = request == null || request.getEmail() == null
                ? ""
                : normalizeEmail(request.getEmail());

        if (email.isBlank()) {
            throw new InvalidCredentialsException("لطفاً ایمیل خود را وارد کنید");
        }

        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isActive() || user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                return;
            }

            String code = String.format("%06d", RANDOM.nextInt(1_000_000));

            passwordResetCodeRepository.save(
                    PasswordResetCode.builder()
                            .email(email)
                            .codeHash(passwordEncoder.encode(code))
                            .expiresAt(LocalDateTime.now().plusMinutes(15))
                            .used(false)
                            .build()
            );

            try {
                smsProvider.send(
                        user.getPhoneNumber(),
                        "کد بازیابی رمز بین‌ما: " + code
                );
            } catch (Exception exception) {
                log.warn("Could not send password reset SMS: {}", exception.getMessage());
            }
        });

        return new LogoutResponse(RESET_NOTICE);
    }


    public LogoutResponse resetPassword(ResetPasswordRequest request) {

        if (request == null
                || request.getEmail() == null
                || request.getEmail().isBlank()
                || request.getCode() == null
                || request.getCode().isBlank()
                || request.getNewPassword() == null
                || request.getNewPassword().isBlank()) {
            throw new InvalidCredentialsException(
                    "ایمیل، کد بازیابی و رمز جدید را وارد کنید"
            );
        }

        if (request.getNewPassword().length() < 6) {
            throw new InvalidCredentialsException(
                    "رمز جدید باید حداقل ۶ کاراکتر باشد"
            );
        }

        String email = normalizeEmail(request.getEmail());
        String code = request.getCode().trim();

        PasswordResetCode stored = passwordResetCodeRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() ->
                        new InvalidCredentialsException("کد بازیابی نامعتبر است")
                );

        if (stored.getExpiresAt() == null
                || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("مهلت کد بازیابی به پایان رسیده است");
        }

        if (!passwordEncoder.matches(code, stored.getCodeHash())) {
            throw new InvalidCredentialsException("کد بازیابی نامعتبر است");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new InvalidCredentialsException("کد بازیابی نامعتبر است")
                );

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        stored.setUsed(true);
        passwordResetCodeRepository.save(stored);

        return new LogoutResponse("رمز عبور با موفقیت تغییر کرد. اکنون وارد شوید.");
    }


    private static String extractBearerToken(

            String authorizationHeader

    ) {

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;

    }



    private String normalizeEmail(

            String email

    ) {


        return email

                .trim()

                .toLowerCase();

    }

}