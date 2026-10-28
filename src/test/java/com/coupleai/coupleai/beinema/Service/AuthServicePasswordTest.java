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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sign-up and sign-in with a username and a password; older accounts sign in with their number. */
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
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(jwt.generateToken(any(), anyString())).thenReturn("token");
    }

    private static RegisterRequest signUp(String username, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static LoginRequest signIn(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private User existing(String username, String password) {
        User user = User.builder().name("").email(username + "@users.beinema").username(username)
                .password(encoder.encode(password)).passwordSet(true)
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        when(users.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void signingUpKeepsTheUsernameInLowerCaseWithAHashedPasswordAndNoNumber() throws Exception {
        AuthResponse response = service.register(signUp("  Sara_M ", "secret-pass"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        User user = saved.getAllValues().get(0);
        assertEquals("sara_m", user.getUsername());
        assertNull(user.getPhoneNumber(), "no mobile number is asked");
        assertTrue(encoder.matches("secret-pass", user.getPassword()));
        assertFalse(user.hasRegisteredName(), "the name is asked in the next step");
        assertEquals("token", response.getToken());
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void aShortPasswordABadNameOrATakenNameIsRefused() {
        IllegalArgumentException shortOne = assertThrows(IllegalArgumentException.class,
                () -> service.register(signUp("sara", "1234567")));
        assertTrue(shortOne.getMessage().contains("۸"), shortOne.getMessage());

        for (String bad : new String[]{"سارا", "1sara", "sa", "sara m", "sara-m"}) {
            assertThrows(IllegalArgumentException.class, () -> service.register(signUp(bad, "secret-pass")), bad);
        }

        when(users.existsByUsername("sara")).thenReturn(true);
        IllegalArgumentException taken = assertThrows(IllegalArgumentException.class,
                () -> service.register(signUp("Sara", "secret-pass")));
        assertTrue(taken.getMessage().contains("گرفته شده"), taken.getMessage());
    }

    @Test
    void anAccountMadeWithANumberSignsInWithThatNumber() {
        User old = User.builder().name("حسین").email(PHONE + "@users.beinema").phoneNumber(PHONE)
                .password(encoder.encode("old-secret-1")).passwordSet(true)
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(old));

        assertEquals("token", service.login(signIn("۰۹۱۲۱۲۳۴۵۶۷", "old-secret-1")).getToken());
        LoginRequest olderClient = new LoginRequest();
        olderClient.setPhoneNumber(PHONE);
        olderClient.setPassword("old-secret-1");
        assertEquals("token", service.login(olderClient).getToken(), "an older page still sends phoneNumber");
    }

    @Test
    void aNumberConfirmedBySmsBeforeStillMakesAnAdmin() {
        User confirmedBySms = User.builder().name("حسین").email("09351112233@users.beinema").phoneNumber("09351112233")
                .password(encoder.encode("p-4-sms-user")).passwordSet(true)
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        when(users.findByPhoneNumber("09351112233")).thenReturn(Optional.of(confirmedBySms));
        ReflectionTestUtils.setField(service, "adminPhones", "09351112233");
        service.login(signIn("09351112233", "p-4-sms-user"));
        assertEquals(UserRole.ADMIN, confirmedBySms.getRole());

        ReflectionTestUtils.setField(service, "adminPhones", "09121234567");
        User fresh = existing("pretend", "pretend-admin");
        service.login(signIn("pretend", "pretend-admin"));
        assertEquals(UserRole.USER, fresh.getRole(), "a username account has no number to be an admin by");
    }

    @Test
    void theRightPasswordSignsInAndAWrongOneDoesNot() {
        existing("sara", "secret-pass");
        assertEquals("token", service.login(signIn("SARA", "secret-pass")).getToken());

        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class,
                () -> service.login(signIn("sara", "secret-pasS")));
        assertEquals("نام کاربری یا رمز درست نیست.", wrong.getMessage());
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> service.login(signIn("nobody", "secret-pass")));
        assertEquals(wrong.getMessage(), unknown.getMessage(), "no hint whether the name has an account");
    }

    @Test
    void manyWrongPasswordsMakeTheNameWaitEvenForTheRightOne() {
        existing("sara", "secret-pass");
        for (int i = 0; i < 8; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.login(signIn("sara", "nope-nope")));
        }
        InvalidCredentialsException waiting = assertThrows(InvalidCredentialsException.class,
                () -> service.login(signIn("sara", "secret-pass")));
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
