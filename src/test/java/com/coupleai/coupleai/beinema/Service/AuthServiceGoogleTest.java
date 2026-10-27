package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.GoogleSignInRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpVerifyRequest;
import com.coupleai.coupleai.beinema.Entity.PhoneLoginCode;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import com.coupleai.coupleai.beinema.Repository.PhoneLoginCodeRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Security.GoogleTokenVerifier;
import com.coupleai.coupleai.beinema.Security.GoogleTokenVerifier.GoogleIdentity;
import com.coupleai.coupleai.beinema.Security.JwtService;
import com.coupleai.coupleai.beinema.Security.TokenBlacklistService;
import com.coupleai.coupleai.beinema.Service.sms.SmsProvider;
import com.coupleai.coupleai.beinema.Util.PhoneNumbers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceGoogleTest {

    private static final String PHONE = PhoneNumbers.normalize("09121234567");

    private UserRepository users;
    private PhoneLoginCodeRepository codes;
    private PasswordEncoder encoder;
    private JwtService jwt;
    private WalletService wallets;
    private ReferralService referrals;
    private GoogleTokenVerifier google;
    private CurrentUserService current;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        codes = mock(PhoneLoginCodeRepository.class);
        encoder = mock(PasswordEncoder.class);
        jwt = mock(JwtService.class);
        wallets = mock(WalletService.class);
        referrals = mock(ReferralService.class);
        google = mock(GoogleTokenVerifier.class);
        current = mock(CurrentUserService.class);

        service = new AuthService(
                users,
                codes,
                encoder,
                jwt,
                mock(TokenBlacklistService.class),
                mock(SmsProvider.class),
                wallets,
                mock(PhoneOtpLockService.class),
                referrals,
                google,
                current
        );
        ReflectionTestUtils.setField(service, "adminPhones", "");
        ReflectionTestUtils.setField(service, "otpBypassPhones", "");
        // linking a number by SMS code is off in production for now; kept working for when it is back
        ReflectionTestUtils.setField(service, "smsLogin", true);

        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(jwt.generateToken(any(), anyString())).thenAnswer(call -> "token-for-" + call.getArgument(1));
        when(google.verify("google-token")).thenReturn(
                new GoogleIdentity("sub-1", "sara@example.com", "Sara", "Ahmadi")
        );
    }

    private static GoogleSignInRequest googleRequest() {
        GoogleSignInRequest request = new GoogleSignInRequest();
        request.setCredential("google-token");
        return request;
    }

    private static User googleUser() {
        User user = User.builder()
                .name("Sara Ahmadi")
                .firstName("Sara")
                .lastName("Ahmadi")
                .email("sara@example.com")
                .googleSubject("sub-1")
                .password("x")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 10L);
        return user;
    }

    private static User phoneUser() {
        User user = User.builder()
                .name("")
                .email(PHONE + "@users.beinema")
                .phoneNumber(PHONE)
                .password("x")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 20L);
        return user;
    }

    private void codeIsSent() {
        when(codes.findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(
                PhoneLoginCode.builder()
                        .phoneNumber(PHONE)
                        .codeHash("code-hash")
                        .expiresAt(LocalDateTime.now().plusMinutes(2))
                        .build()
        ));
        when(encoder.matches("1234", "code-hash")).thenReturn(true);
    }

    private static OtpVerifyRequest linkRequest(String code) {
        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setPhoneNumber("09121234567");
        request.setCode(code);
        request.setReferralCode("FRIEND");
        return request;
    }

    @Test
    void aNewGoogleUserIsCompleteWithoutAPhone() {
        when(users.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(users.findByEmail("sara@example.com")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        GoogleSignInRequest request = googleRequest();
        request.setReferralCode("FRIEND1");

        AuthResponse response = service.signInWithGoogle(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        User user = saved.getValue();
        assertEquals("sub-1", user.getGoogleSubject());
        assertEquals("sara@example.com", user.getEmail());
        assertEquals("Sara Ahmadi", user.getName());
        assertTrue(user.isEmailVerified());
        assertNull(user.getPhoneNumber());

        // signing in with Google is a full way in: no phone step, wallet and referral at once
        assertFalse(response.isPhoneRequired());
        assertTrue(response.isProfileComplete());
        assertEquals("token-for-sara@example.com", response.getToken());
        verify(wallets).getOrCreate(any());
        verify(referrals).rewardNewUser(any(), eq("FRIEND1"));
    }

    @Test
    void aReturningGoogleUserIsFoundByItsGoogleId() {
        User user = phoneUser();
        user.setGoogleSubject("sub-1");
        when(users.findByGoogleSubject("sub-1")).thenReturn(Optional.of(user));

        AuthResponse response = service.signInWithGoogle(googleRequest());

        assertFalse(response.isPhoneRequired());
        assertEquals("token-for-" + user.getEmail(), response.getToken());
        verify(users, never()).existsByEmail(anyString());
    }

    @Test
    void anAccountWithTheSameEmailIsLinkedToGoogle() {
        User user = googleUser();
        user.setGoogleSubject(null);
        user.setPhoneNumber(PHONE);
        when(users.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(users.findByEmail("sara@example.com")).thenReturn(Optional.of(user));

        service.signInWithGoogle(googleRequest());

        assertEquals("sub-1", user.getGoogleSubject());
    }

    @Test
    void anInactiveAccountCannotSignIn() {
        User user = googleUser();
        user.setStatus(UserStatus.BLOCKED);
        when(users.findByGoogleSubject("sub-1")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () -> service.signInWithGoogle(googleRequest()));
    }

    @Test
    void aNewNumberBecomesTheGoogleAccountsNumber() {
        User user = googleUser();
        when(current.getCurrentUser()).thenReturn(user);
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        codeIsSent();

        AuthResponse response = service.verifyPhoneLink(linkRequest("1234"));

        assertEquals(PHONE, user.getPhoneNumber());
        assertFalse(response.isPhoneRequired());
        verify(wallets).getOrCreate(user);
        verify(referrals).rewardNewUser(user, "FRIEND");
    }

    @Test
    void aWrongCodeLinksNothing() {
        User user = googleUser();
        when(current.getCurrentUser()).thenReturn(user);
        codeIsSent();

        assertThrows(InvalidCredentialsException.class, () -> service.verifyPhoneLink(linkRequest("9999")));
        assertNull(user.getPhoneNumber());
    }

    @Test
    void aNumberWithAnAccountJoinsThatAccount() {
        User temporary = googleUser();
        User owner = phoneUser();
        when(current.getCurrentUser()).thenReturn(temporary);
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(owner));
        codeIsSent();

        AuthResponse response = service.verifyPhoneLink(linkRequest("1234"));

        assertEquals("sub-1", owner.getGoogleSubject());
        assertEquals("Sara Ahmadi", owner.getName());
        assertTrue(temporary.isDeleted());
        assertNull(temporary.getGoogleSubject());
        assertEquals("token-for-" + owner.getEmail(), response.getToken());
        assertFalse(response.isPhoneRequired());
        verify(wallets, never()).getOrCreate(any());
        verify(referrals, never()).rewardNewUser(any(), any());
    }

    @Test
    void aNumberOfAnotherGoogleAccountIsRefused() {
        User temporary = googleUser();
        User owner = phoneUser();
        owner.setGoogleSubject("sub-other");
        when(current.getCurrentUser()).thenReturn(temporary);
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(owner));
        codeIsSent();

        assertThrows(IllegalArgumentException.class, () -> service.verifyPhoneLink(linkRequest("1234")));
        assertFalse(temporary.isDeleted());
        assertEquals("sub-other", owner.getGoogleSubject());
    }

    @Test
    void anAccountThatHasANumberCannotLinkAnother() {
        when(current.getCurrentUser()).thenReturn(phoneUser());

        assertThrows(IllegalArgumentException.class, () -> service.verifyPhoneLink(linkRequest("1234")));
        verify(codes, never()).findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(anyString());
    }

    @Test
    void anAdminNumberMakesTheAccountAdmin() {
        ReflectionTestUtils.setField(service, "adminPhones", "09121234567");
        User user = googleUser();
        when(current.getCurrentUser()).thenReturn(user);
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        codeIsSent();

        AuthResponse response = service.verifyPhoneLink(linkRequest("1234"));

        assertEquals("ADMIN", response.getRole());
        verify(jwt).generateToken(eq(10L), eq("sara@example.com"));
    }
}
