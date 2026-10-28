package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.SessionPackage;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Entity.Wallet;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import com.coupleai.coupleai.beinema.Exception.SessionOnBreakException;
import com.coupleai.coupleai.beinema.Repository.SessionPackageRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private SessionPackageRepository packages;
    private SessionBillingService billing;
    private User owner;
    private User partner;
    private Wallet wallet;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        wallets = mock(WalletService.class);
        packages = mock(SessionPackageRepository.class);
        billing = new SessionBillingService(users, wallets, packages);
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

    private SessionPackage bundle(int total, int used) {
        SessionPackage bundle = new SessionPackage();
        bundle.setUserId(1L);
        bundle.setTier("BRONZE");
        bundle.setSessionsTotal(total);
        bundle.setSessionsUsed(used);
        bundle.setPrice(300_000L);
        bundle.setExpiresAt(Instant.now().plus(Duration.ofDays(20)));
        return bundle;
    }

    @Test
    void afterTheFreeOnesABundleIsUsedBeforeTheWallet() {
        owner.setFreeSessionsUsed(2);
        wallet.setBalance(0L);
        SessionPackage bronze = bundle(4, 1);
        when(packages.findUsableForUpdate(eq(1L), any())).thenReturn(List.of(bronze));
        when(packages.findUsable(eq(1L), any())).thenReturn(List.of(bronze));

        assertDoesNotThrow(() -> billing.requireCanStart(room, owner), "a bundle lets the session start without credit");
        assertEquals(SessionBillingService.PACKAGE, billing.settle(room, 3));
        assertEquals(2, bronze.getSessionsUsed());
        verify(wallets, never()).debit(any(), any(), anyLong(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void aBundleIsBoughtFromTheWalletForAMonth() {
        wallet.setBalance(700_000L);
        when(packages.save(any(SessionPackage.class))).thenAnswer(call -> call.getArgument(0));
        SessionBillingService.OwnedPackage silver = billing.buyPackage(owner, "silver");

        assertEquals(10, silver.sessionsLeft());
        assertEquals("نقره‌ای", silver.title());
        long days = Duration.between(Instant.now(), Instant.ofEpochMilli(silver.expiresAt())).toDays();
        assertTrue(days >= 29 && days <= 30, "valid for a month, was " + days);
        verify(wallets).debit(eq(owner), eq(wallet), eq(700_000L), eq(WalletTransactionType.PACKAGE_PURCHASE),
                anyString(), anyString(), eq(null), eq(null));
    }

    @Test
    void notEnoughCreditForABundleSaysHowMuchIsNeeded() {
        wallet.setBalance(1_000_000L);
        IllegalArgumentException gold = assertThrows(IllegalArgumentException.class, () -> billing.buyPackage(owner, "GOLD"));
        assertTrue(gold.getMessage().contains("۱٬۵۰۰٬۰۰۰ تومان"), gold.getMessage());
        assertThrows(IllegalArgumentException.class, () -> billing.buyPackage(owner, "PLATINUM"));
    }

    @Test
    void biggerBundlesCostLessPerSessionAndTheStruckPriceIsTheUsualOne() {
        List<SessionBillingService.PackageOffer> offers = billing.packageOffers();
        assertEquals(List.of(75_000L, 70_000L, 62_500L), offers.stream().map(SessionBillingService.PackageOffer::perSession).toList());
        assertEquals(List.of(2_000_000L, 5_000_000L, 12_000_000L), offers.stream().map(SessionBillingService.PackageOffer::listPrice).toList());
        assertTrue(offers.stream().allMatch(offer -> offer.validDays() == 30));
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
