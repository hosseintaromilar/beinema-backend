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
        billing = new SessionBillingService(users, org.mockito.Mockito.mock(com.coupleai.coupleai.beinema.ai.AiHealth.class), wallets, packages, org.mockito.Mockito.mock(com.coupleai.coupleai.beinema.Repository.ChatRoomRepository.class),
                org.mockito.Mockito.mock(com.coupleai.coupleai.beinema.Repository.PromoTierCounterRepository.class));
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

    private com.coupleai.coupleai.beinema.Repository.PromoTierCounterRepository counters() {
        return (com.coupleai.coupleai.beinema.Repository.PromoTierCounterRepository)
                ReflectionTestUtils.getField(billing, "counterRepository");
    }

    @Test
    void theFirstBuyersGetTheFirstStepAndTheServerDecidesThePrice() {
        wallet.setBalance(2_000_000L);
        when(packages.save(any(SessionPackage.class))).thenAnswer(call -> call.getArgument(0));
        when(counters().take("MAIN", 1)).thenReturn(1);
        SessionBillingService.OwnedPackage main = billing.buyPackage(owner, "main", 995_000L);

        assertEquals(8, main.sessionsLeft());
        assertEquals("بستهٔ همراه", main.title());
        long days = Duration.between(Instant.now(), Instant.ofEpochMilli(main.expiresAt())).toDays();
        assertTrue(days >= 59 && days <= 60, "valid 60 days, was " + days);
        verify(wallets).debit(eq(owner), eq(wallet), eq(995_000L), eq(WalletTransactionType.PACKAGE_PURCHASE),
                anyString(), anyString(), eq(null), eq(null));
    }

    @Test
    void aFullStepGivesTheNextOne_andAChangedPriceBuysNothing() {
        wallet.setBalance(2_000_000L);
        when(counters().take("MAIN", 1)).thenReturn(0);
        when(counters().take("MAIN", 2)).thenReturn(1);
        SessionBillingService.PriceChangedException changed = assertThrows(SessionBillingService.PriceChangedException.class,
                () -> billing.buyPackage(owner, "MAIN", 995_000L));
        assertEquals(1_393_000L, changed.price());
        verify(wallets, never()).debit(any(), any(), anyLong(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void notEnoughCreditForABundleSaysHowMuchIsNeeded() {
        wallet.setBalance(100_000L);
        IllegalArgumentException trial = assertThrows(IllegalArgumentException.class, () -> billing.buyPackage(owner, "TRIAL"));
        assertTrue(trial.getMessage().contains("۵۹۰٬۰۰۰ تومان"), trial.getMessage());
        assertThrows(IllegalArgumentException.class, () -> billing.buyPackage(owner, "PLATINUM"));
    }

    @Test
    void theCardsShowTheActiveStepAndTheRegularPrice() {
        List<SessionBillingService.PackageOffer> offers = billing.packageOffers();
        assertEquals(List.of(413_000L, 995_000L, 2_245_000L, 495_000L),
                offers.stream().map(SessionBillingService.PackageOffer::price).toList());
        assertEquals(List.of(590_000L, 1_990_000L, 4_490_000L, 990_000L),
                offers.stream().map(SessionBillingService.PackageOffer::listPrice).toList());
        assertEquals(List.of(1, 2, 3, 1), offers.stream().map(SessionBillingService.PackageOffer::dailySessions).toList());
    }

    @Test
    void cancellingPaysBackTheUnusedSessionsAtTheRegularPrice() {
        SessionPackage main = bundle(8, 3);
        main.setTier("MAIN");
        main.setPrice(995_000L);
        main.setRegularPrice(1_990_000L);
        assertEquals(248_750L, SessionBillingService.refundFor(main));
        main.setSessionsUsed(6);
        assertEquals(0L, SessionBillingService.refundFor(main));
        SessionPackage diamond = bundle(20, 5);
        diamond.setTier("DIAMOND");
        diamond.setPrice(3_143_000L);
        diamond.setRegularPrice(4_490_000L);
        assertEquals(2_020_500L, SessionBillingService.refundFor(diamond));
        SessionPackage trial = bundle(2, 0);
        trial.setTier("TRIAL");
        assertEquals(0L, SessionBillingService.refundFor(trial), "the trial has no refund");
    }

    @Test
    void theBestActiveBundleDecidesTheDailyLimit() {
        ReflectionTestUtils.setField(billing, "dailySessionsWithoutBundle", 1);
        assertEquals(1, billing.dailyLimitFor(1L), "no bundle");

        SessionPackage trial = bundle(2, 0);
        trial.setTier("TRIAL");
        SessionPackage diamond = bundle(20, 3);
        diamond.setTier("DIAMOND");
        when(packages.findUsable(eq(1L), any())).thenReturn(List.of(trial, diamond));
        assertEquals(3, billing.dailyLimitFor(1L));
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
