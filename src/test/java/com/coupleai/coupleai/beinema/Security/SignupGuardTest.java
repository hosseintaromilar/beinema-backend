package com.coupleai.coupleai.beinema.Security;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignupGuardTest {

    private static final String DEVICE = "3f2a9c1e-7b44-4d0a-9e1f-0c6d2b8a5e77";
    private final UserRepository users = mock(UserRepository.class);
    private final SignupGuard guard = new SignupGuard(users, 2, 20);
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "5.1.2.3, 10.0.0.1");
        request.addHeader("X-Device-Id", DEVICE);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void twoAccountsADayFromOneDevice() {
        when(users.countByCreatedAtAfterAndSignupDevice(any(), eq(DEVICE))).thenReturn(1L);
        assertDoesNotThrow(guard::requireRoom);
        when(users.countByCreatedAtAfterAndSignupDevice(any(), eq(DEVICE))).thenReturn(2L);
        assertThrows(IllegalArgumentException.class, guard::requireRoom, "the third one waits a day");
    }

    @Test
    void manyPeopleShareAnAddressSoItsLimitIsWide() {
        when(users.countByCreatedAtAfterAndSignupIp(any(), eq("5.1.2.3"))).thenReturn(19L);
        assertDoesNotThrow(guard::requireRoom);
        when(users.countByCreatedAtAfterAndSignupIp(any(), eq("5.1.2.3"))).thenReturn(20L);
        assertThrows(IllegalArgumentException.class, guard::requireRoom);
    }

    @Test
    void aNewAccountKeepsWhereItWasMadeAndAMadeUpIdIsIgnored() {
        User user = new User();
        guard.stamp(user);
        assertEquals("5.1.2.3", user.getSignupIp());
        assertEquals(DEVICE, user.getSignupDevice());

        request.removeHeader("X-Device-Id");
        request.addHeader("X-Device-Id", "<script>");
        User other = new User();
        guard.stamp(other);
        assertNull(other.getSignupDevice());
    }
}
