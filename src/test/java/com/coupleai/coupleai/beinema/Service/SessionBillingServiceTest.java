package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Entity.Wallet;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import com.coupleai.coupleai.beinema.Exception.SessionOnBreakException;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionBillingServiceTest {

    private UserRepository users;
    private WalletService wallets;
    private SessionBillingService billing;
    private User owner;
    private User partner;
    private Wallet wallet;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        wallets = mock(WalletService.class);
        billing = new SessionBillingService(users, wallets);
        ReflectionTestUtils.setField(billing, "freeSessions", 2);
        ReflectionTestUtils.setField(billing, "sessionPrice", 100_000L);
        ReflectionTestUtils.setField(billing, "sessionListPrice", 500_000L);

        owner = new User();
        owner.setId(1L);
        owner.setName("افسانه");
        partner = new User();
        partner.setId(2L);
        partner.setName("حسین");
        wallet = Wallet.builder().user(owner).balance(0L).build();
        room = new ChatRoom();
        room.setId(7L);
        room.setTitle("دعواهای شبانه");
        room.setCreatedBy(1L);

        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(wallets.getOrCreate(owner)).thenReturn(wallet);
        when(wallets.lockByUserId(1L)).thenReturn(wallet);
    }

    @Test
    void theFirstTwoSessionsAreFreeAndCostNothing() {
        assertEquals(SessionBillingService.FREE, billing.settle(room, 1));
        assertEquals(SessionBillingService.FREE, billing.settle(room, 2));
        assertEquals(2, owner.getFreeSessionsUsed());
        verify(wallets, never()).debit(any(), any(), anyLong(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void afterTheFreeOnesTheCreatorPaysTheSessionPrice() {
        owner.setFreeSessionsUsed(2);
        wallet.setBalance(250_000L);
        assertEquals(SessionBillingService.PAID, billing.settle(room, 3));
        verify(wallets).debit(eq(owner), eq(wallet), eq(100_000L), eq(WalletTransactionType.SESSION_FEE),
                anyString(), eq("session:7:3"), eq(7L), eq(null));
    }

    @Test
    void aShortWalletAtTheEndIsMarkedUnpaidInsteadOfFailingTheClose() {
        owner.setFreeSessionsUsed(2);
        wallet.setBalance(40_000L);
        assertEquals(SessionBillingService.UNPAID, billing.settle(room, 3));
        verify(wallets, never()).debit(any(), any(), anyLong(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void noFreeSessionAndNoCreditStopsTheStartWithWordsForWhoAsks() {
        owner.setFreeSessionsUsed(2);
        wallet.setBalance(0L);
        SessionOnBreakException toOwner = assertThrows(SessionOnBreakException.class,
                () -> billing.requireCanStart(room, owner));
        assertEquals(SessionOnBreakException.PAYMENT, toOwner.getCode());
        assertTrue(toOwner.getMessage().contains("حسابت را شارژ کن"), toOwner.getMessage());
        assertTrue(toOwner.getMessage().contains("۱۰۰٬۰۰۰ تومان"), toOwner.getMessage());

        SessionOnBreakException toPartner = assertThrows(SessionOnBreakException.class,
                () -> billing.requireCanStart(room, partner));
        assertTrue(toPartner.getMessage().contains("افسانه باید اعتبار حسابش را شارژ کند"), toPartner.getMessage());
    }

    @Test
    void aFreeSessionLeftOrEnoughCreditLetsTheSessionStart() {
        owner.setFreeSessionsUsed(1);
        assertDoesNotThrow(() -> billing.requireCanStart(room, partner));
        owner.setFreeSessionsUsed(2);
        wallet.setBalance(100_000L);
        assertDoesNotThrow(() -> billing.requireCanStart(room, owner));
    }
}
