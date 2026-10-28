package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.User.ChangePasswordRequest;
import com.coupleai.coupleai.beinema.DTO.User.UpdateProfileRequest;
import com.coupleai.coupleai.beinema.DTO.User.UserResponse;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Editing one's own profile and setting or changing one's password. */
class UserServiceProfileTest {

    private static final String EMAIL = "09121234567@users.beinema";

    private UserRepository users;
    private BCryptPasswordEncoder encoder;
    private UserService service;
    private User me;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new UserService(users, mock(FileStorageService.class), encoder);
        me = User.builder().name("سارا محمدی").firstName("سارا").lastName("محمدی").email(EMAIL)
                .phoneNumber("09121234567").phoneVerified(false).password(encoder.encode("old-secret-1")).passwordSet(true)
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        me.setId(5L);
        auth = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(me));
        when(users.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static UpdateProfileRequest profile(String first, String last, String phone) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName(first);
        request.setLastName(last);
        request.setPhoneNumber(phone);
        return request;
    }

    private static ChangePasswordRequest password(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    @Test
    void theNameAndANewNumberAreSavedTheNumberUnconfirmed() {
        me.setPhoneVerified(null);
        UserResponse response = service.updateProfile(auth, profile("سارا", "کریمی", "۰۹۳۵۱۱۱۲۲۳۳"));
        assertEquals("سارا کریمی", response.getName());
        assertEquals("09351112233", me.getPhoneNumber());
        assertEquals(Boolean.FALSE, me.getPhoneVerified(), "a typed number proves nothing until SMS is back");
    }

    @Test
    void aNumberOfAnotherAccountOrABadNameIsRefused() {
        User other = User.builder().email("x@users.beinema").phoneNumber("09351112233").build();
        other.setId(9L);
        when(users.findByPhoneNumber("09351112233")).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(auth, profile("سارا", "کریمی", "09351112233")));
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(auth, profile("س", "کریمی", "09121234567")));
        assertEquals("09121234567", me.getPhoneNumber());
    }

    @Test
    void changingThePasswordAsksForTheCurrentOne() {
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(auth, password("not-it-at-all", "new-secret-2")));
        assertEquals("رمز فعلی درست نیست.", wrong.getMessage());
        assertTrue(encoder.matches("old-secret-1", me.getPassword()));

        service.changePassword(auth, password("old-secret-1", "new-secret-2"));
        assertTrue(encoder.matches("new-secret-2", me.getPassword()));
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(auth, password("new-secret-2", "short")));
    }

    @Test
    void aGoogleAccountSetsItsFirstPasswordWithoutACurrentOne() {
        me.setPasswordSet(null);
        me.setGoogleSubject("g-1");
        me.setPassword(encoder.encode("random-uuid"));
        UserResponse response = service.changePassword(auth, password(null, "my-first-pass"));
        assertTrue(response.isPasswordSet());
        assertTrue(response.isGoogleLinked());
        assertTrue(encoder.matches("my-first-pass", me.getPassword()));

        assertThrows(IllegalArgumentException.class, () -> service.changePassword(auth, password(null, "another-pass")),
                "now that it has one, the current password is asked");
    }

    @Test
    void withoutANumberThereIsNothingToSignInWith() {
        me.setPasswordSet(null);
        me.setPhoneNumber(null);
        IllegalArgumentException noPhone = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(auth, password(null, "my-first-pass")));
        assertTrue(noPhone.getMessage().contains("شماره"));
        assertFalse(Boolean.TRUE.equals(me.getPasswordSet()));
    }
}
