package com.coupleai.coupleai.beinema.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialInflationTest {

    /** As the Statistical Center's home page wrote it on 1405/08/16, with the tags around the figures. */
    private static final String PAGE = "<div class=\"stat\"><span>نرخ تورم مصرف‌کننده</span><b>%76/8</b><small>مهر 1405</small></div>"
            + "<div class=\"stat\"><span>نرخ تورم تولیدکننده</span><b>%66/7</b><small>بهار 1405</small></div>"
            + "<div><span>نرخ بیکاری</span><b>%9/1</b><small>بهار 1405</small></div>";

    @Test
    void theRatesAndTheirPeriodsAreReadFromTheHomePage() {
        OfficialInflation.Rates rates = OfficialInflation.parse(PAGE);
        assertEquals(76.8, rates.consumer().percent(), 1e-9);
        assertEquals("مهر 1405", rates.consumer().period());
        assertEquals(66.7, rates.producer().percent(), 1e-9);
        assertEquals("بهار 1405", rates.producer().period());
    }

    @Test
    void persianDigitsAndAMissingRateAreHandled() {
        OfficialInflation.Rates rates = OfficialInflation.parse("<p>نرخ تورم مصرف‌کننده ۷۶٫۸% آبان ۱۴۰۵</p>");
        assertEquals(76.8, rates.consumer().percent(), 1e-9);
        assertEquals("آبان ۱۴۰۵", rates.consumer().period());
        assertNull(rates.producer());
        assertNull(OfficialInflation.parse("<p>صفحهٔ دیگر</p>").consumer());
    }

    @Test
    void theBrainGetsTheOfficialRateWithItsPeriodOrIsToldItIsMissing() {
        String line = MarketDataService.inflationLine(OfficialInflation.parse(PAGE));
        assertTrue(line.contains("consumer 76.8% (مهر 1405)"));
        assertTrue(line.contains("producer 66.7% (بهار 1405)"));
        assertTrue(line.contains("amar.org.ir"));
        assertTrue(MarketDataService.inflationLine(null).startsWith("official inflation: unavailable"));
    }
}
