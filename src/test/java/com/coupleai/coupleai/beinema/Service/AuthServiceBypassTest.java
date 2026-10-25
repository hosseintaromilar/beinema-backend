package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpPhoneRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpRequestResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpVerifyRequest;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import com.coupleai.coupleai.beinema.Repository.PhoneLoginCodeRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Security.GoogleTokenVerifier;
import com.coupleai.coupleai.beinema.Security.JwtService;
import com.coupleai.coupleai.beinema.Security.TokenBlacklistService;
import com.coupleai.coupleai.beinema.Service.sms.SmsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The admins' test numbers: no SMS; with a fixed code set, only that code signs them in. */
class AuthServiceBypassTest {

    private static final String ADMIN = "09914816794";

    private UserRepository users;
    private SmsProvider sms;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        sms = mock(SmsProvider.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtService jwt = mock(JwtService.class);
        service = new AuthService(users, mock(PhoneLoginCodeRepository.class), encoder, jwt,
                mock(TokenBlacklistService.class), sms, mock(WalletService.class), mock(PhoneOtpLockService.class),
                mock(ReferralService.class), mock(GoogleTokenVerifier.class), mock(CurrentUserService.class));
        ReflectionTestUtils.setField(service, "adminPhones", "09914816794,09379736278");
        ReflectionTestUtils.setField(service, "otpBypassPhones", "09914816794, 09379736278");
        ReflectionTestUtils.setField(service, "otpBypassCode", "");

        User admin = User.builder().name("حسین").email(ADMIN + "@users.beinema").phoneNumber(ADMIN)
                .password("x").status(UserStatus.ACTIVE).role(UserRole.USER).build();
        when(users.findByPhoneNumber(ADMIN)).thenReturn(Optional.of(admin));
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(jwt.generateToken(any(), anyString())).thenReturn("token");
    }

    private static OtpPhoneRequest request(String phone) {
        OtpPhoneRequest request = new OtpPhoneRequest();
        request.setPhoneNumber(phone);
        return request;
    }

    private static OtpVerifyRequest code(String code) {
        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setPhoneNumber(ADMIN);
        request.setCode(code);
        return request;
    }

    @Test
    void withoutAFixedCodeTheyAreSignedInDirectly() {
        OtpRequestResponse response = service.requestOtp(request(ADMIN));

        assertTrue(response.isSignedIn());
        assertEquals("ADMIN", response.getRole());
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void withAFixedCodeNoSmsIsSentAndOnlyThatCodeSignsIn() {
        ReflectionTestUtils.setField(service, "otpBypassCode", "4821");

        OtpRequestResponse asked = service.requestOtp(request("+989914816794"));
        assertFalse(asked.isSignedIn(), "the code is still needed");
        assertTrue(asked.getMessage().contains("کد ثابت"));
        verify(sms, never()).send(anyString(), anyString());

        assertThrows(InvalidCredentialsException.class, () -> service.verifyOtp(code("1111")));
        AuthResponse session = service.verifyOtp(code("4821"));
        assertEquals("token", session.getToken());
        assertEquals("ADMIN", session.getRole());
    }

    @Test
    void guessingTheFixedCodeIsStoppedAfterFiveTries() {
        ReflectionTestUtils.setField(service, "otpBypassCode", "4821");
        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.verifyOtp(code("0000")));
        }
        InvalidCredentialsException locked = assertThrows(InvalidCredentialsException.class,
                () -> service.verifyOtp(code("4821")));
        assertTrue(locked.getMessage().contains("یک ساعت"), "even the right code waits after five wrong ones");
    }
}
