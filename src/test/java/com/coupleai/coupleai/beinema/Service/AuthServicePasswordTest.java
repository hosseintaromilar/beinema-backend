package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.LoginRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpPhoneRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.RegisterRequest;
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
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

/** Sign-up and sign-in with the mobile number and a password, while SMS codes are off. */
class AuthServicePasswordTest {

    private static final String PHONE = "09121234567";

    private UserRepository users;
    private SmsProvider sms;
    private BCryptPasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        sms = mock(SmsProvider.class);
        encoder = new BCryptPasswordEncoder(4);
        JwtService jwt = mock(JwtService.class);
        service = new AuthService(users, mock(PhoneLoginCodeRepository.class), encoder, jwt,
                mock(TokenBlacklistService.class), sms, mock(WalletService.class), mock(PhoneOtpLockService.class),
                mock(ReferralService.class), mock(GoogleTokenVerifier.class), mock(CurrentUserService.class));
        ReflectionTestUtils.setField(service, "adminPhones", "");
        ReflectionTestUtils.setField(service, "otpBypassPhones", "");
        ReflectionTestUtils.setField(service, "otpBypassCode", "");
        when(users.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(jwt.generateToken(any(), anyString())).thenReturn("token");
    }

    private static RegisterRequest signUp(String phone, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setPhoneNumber(phone);
        request.setPassword(password);
        return request;
    }

    private static LoginRequest signIn(String phone, String password) {
        LoginRequest request = new LoginRequest();
        request.setPhoneNumber(phone);
        request.setPassword(password);
        return request;
    }

    private User existing(String password) {
        User user = User.builder().name("").email(PHONE + "@users.beinema").phoneNumber(PHONE)
                .password(encoder.encode(password)).status(UserStatus.ACTIVE).role(UserRole.USER).build();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void signingUpWritesTheNumberDownUnconfirmedWithAHashedPasswordAndNoSms() throws Exception {
        AuthResponse response = service.register(signUp("۰۹۱۲۱۲۳۴۵۶۷", "secret-pass"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        User user = saved.getAllValues().get(0);
        assertEquals(PHONE, user.getPhoneNumber(), "Persian digits become the usual number");
        assertEquals(Boolean.FALSE, user.getPhoneVerified());
        assertTrue(encoder.matches("secret-pass", user.getPassword()));
        assertFalse(user.hasRegisteredName(), "the name is asked in the next step");
        assertEquals("token", response.getToken());
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void aShortPasswordOrATakenNumberIsRefused() {
        IllegalArgumentException shortOne = assertThrows(IllegalArgumentException.class,
                () -> service.register(signUp(PHONE, "1234567")));
        assertTrue(shortOne.getMessage().contains("۸"), shortOne.getMessage());

        existing("whatever-1");
        IllegalArgumentException taken = assertThrows(IllegalArgumentException.class,
                () -> service.register(signUp(PHONE, "secret-pass")));
        assertTrue(taken.getMessage().contains("وارد شو"), taken.getMessage());
    }

    @Test
    void theRightPasswordSignsInAndAWrongOneDoesNot() {
        existing("secret-pass");
        assertEquals("token", service.login(signIn(PHONE, "secret-pass")).getToken());

        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class,
                () -> service.login(signIn(PHONE, "secret-pasS")));
        assertEquals("شماره موبایل یا رمز درست نیست.", wrong.getMessage());
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> service.login(signIn("09351112233", "secret-pass")));
        assertEquals(wrong.getMessage(), unknown.getMessage(), "no hint whether the number has an account");
    }

    @Test
    void manyWrongPasswordsMakeTheNumberWaitEvenForTheRightOne() {
        existing("secret-pass");
        for (int i = 0; i < 8; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.login(signIn(PHONE, "nope-nope")));
        }
        InvalidCredentialsException waiting = assertThrows(InvalidCredentialsException.class,
                () -> service.login(signIn(PHONE, "secret-pass")));
        assertTrue(waiting.getMessage().contains("ربع ساعت"), waiting.getMessage());
    }

    @Test
    void smsCodesAreOffByDefault() throws Exception {
        OtpPhoneRequest request = new OtpPhoneRequest();
        request.setPhoneNumber(PHONE);
        InvalidCredentialsException off = assertThrows(InvalidCredentialsException.class,
                () -> service.requestOtp(request));
        assertTrue(off.getMessage().contains("رمز"), off.getMessage());
        verify(sms, never()).send(anyString(), anyString());
    }
}
