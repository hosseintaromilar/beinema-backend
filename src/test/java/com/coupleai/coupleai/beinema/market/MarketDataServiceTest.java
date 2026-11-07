package com.coupleai.coupleai.beinema.market;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataServiceTest {

    @Test
    void datesAreShownInTheIranianCalendar() {
        assertEquals("1405/08/16", MarketDataService.jalali(LocalDate.of(2026, 11, 7)));
        assertEquals("1405/01/01", MarketDataService.jalali(LocalDate.of(2026, 3, 21)));
        assertEquals("1404/12/29", MarketDataService.jalali(LocalDate.of(2026, 3, 20)));
    }

    @Test
    void aChangeComparesWithTheLastCloseOnOrBeforeThatDay() {
        LocalDate today = LocalDate.of(2026, 11, 7);
        List<double[]> closes = List.of(
                new double[]{today.minusDays(1).toEpochDay(), 110},
                new double[]{today.minusDays(9).toEpochDay(), 100},   // a week ago was a holiday: the day before counts
                new double[]{today.minusDays(40).toEpochDay(), 80});
        assertEquals(100.0, MarketDataService.closeOnOrBefore(closes, today.minusDays(7)));
        assertEquals(10.0, MarketDataService.change(110, 100.0), 1e-9);
        assertNull(MarketDataService.closeOnOrBefore(closes, today.minusDays(60)));
        assertNull(MarketDataService.change(110, null));
        assertEquals("unknown", MarketDataService.percent(null));
        assertEquals("-3.4%", MarketDataService.percent(-3.41));
    }

    @Test
    void theBlockNamesItsSourceAndTimeAndSaysWhenItIsOldOrMissing() {
        Instant at = Instant.parse("2026-11-07T13:00:00Z");
        List<MarketDataService.Quote> quotes = List.of(
                new MarketDataService.Quote("دلار آزاد", 266900, "تومان", 0.5, 1.2, -3.4));
        String fresh = MarketDataService.block(quotes, at, at.plus(Duration.ofHours(1)));
        assertTrue(fresh.contains("tgju.org"));
        assertTrue(fresh.contains("1405/08/16 16:30"));
        assertTrue(fresh.contains("- دلار آزاد: 266,900 تومان | today +0.5% | week +1.2% | month -3.4%"));
        assertFalse(fresh.contains("STALE"));
        assertTrue(fresh.contains("not here: bank deposit rates"));

        assertTrue(MarketDataService.block(quotes, at, at.plus(Duration.ofHours(7))).contains("STALE"));
        assertTrue(MarketDataService.block(List.of(), null, at).startsWith("unavailable"));
    }
}
