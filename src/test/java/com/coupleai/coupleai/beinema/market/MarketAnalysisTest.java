package com.coupleai.coupleai.beinema.market;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The analyst's figures: returns, the deepest fall, the trend, the return after inflation, the coin premium, and the report. */
class MarketAnalysisTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    /** A price that doubles in a straight line over a year, with a 20% dip in the middle. */
    private static List<double[]> year() {
        List<double[]> series = new ArrayList<>();
        for (int i = 400; i >= 1; i--) {
            double price = 100 + (400 - i) * 0.25;
            if (i > 170 && i < 200) {
                price *= 0.8;
            }
            series.add(new double[]{TODAY.minusDays(i).toEpochDay(), price});
        }
        return series;
    }

    @Test
    void readsAYearLikeAnAnalyst() {
        MarketAnalysis.Analysis a = MarketAnalysis.of("سکهٔ امامی", "تومان", 200, year(), TODAY, 40.0);
        assertEquals(200.0 / (100 + 35 * 0.25) * 100 - 100, a.changes().get("year"), 0.5);
        assertTrue(a.maxDrawdown() < -15, "the dip in the middle is the deepest fall");
        assertEquals(0.0, a.fromHigh(), 0.01);
        assertEquals("UP", a.trend());
        assertNotNull(a.volatility());
        // after inflation: (1 + r) / 1.4 - 1
        assertEquals(((1 + a.changes().get("year") / 100) / 1.4 - 1) * 100, a.realYear(), 0.01);
    }

    @Test
    void aShortHistoryHasNoYearFigures() {
        List<double[]> short1 = year().subList(390, 400);
        MarketAnalysis.Analysis a = MarketAnalysis.of("یورو", "تومان", 200, short1, TODAY, null);
        assertNull(a.changes().get("year"));
        assertNull(a.trend());
        assertNull(a.realYear());
    }

    @Test
    void theCoinPremiumIsOverTheGoldInIt() {
        // ounce 3,000 $, dollar 100,000 tomans: the gold of a coin is worth 3000/31.1035*7.3197*100000
        double gold = 3000 / MarketAnalysis.OUNCE * 8.133 * 0.9 * 100_000;
        assertEquals(10.0, MarketAnalysis.premium("سکهٔ امامی", gold * 1.1, 3000.0, 100_000.0), 0.01);
        assertNull(MarketAnalysis.premium("دلار آزاد", 1, 3000.0, 100_000.0));
        assertEquals(-12.5, MarketAnalysis.realRate(20.5, 37.71), 0.1);
    }

    @Test
    void theReportCarriesItsChartsSourcesAndPlainSentences() {
        MarketDataService.Snapshot now = new MarketDataService.Snapshot(
                List.of(new MarketDataService.Quote("دلار آزاد", 200, "تومان", 0.5, 1.0, 2.0),
                        new MarketDataService.Quote("سکهٔ امامی", 300, "تومان", 0.2, 1.0, 3.0)),
                Instant.now(), new OfficialInflation.Rates(new OfficialInflation.Rate(40, "مهر 1405"), null),
                Map.of("دلار آزاد", year(), "سکهٔ امامی", year()));
        Map<String, Object> report = MarketReportService.build(now, FinanceReferenceService.file(), TODAY);
        assertEquals(MarketReportService.SCHEMA, report.get("schema"));
        List<?> assets = (List<?>) report.get("assets");
        assertEquals(2, assets.size());
        assertTrue(((List<?>) ((Map<?, ?>) assets.get(0)).get("series")).size() <= MarketReportService.POINTS);
        Map<?, ?> growth = (Map<?, ?>) report.get("growth");
        assertTrue(growth.containsKey("دلار آزاد") && growth.containsKey("تورم") && growth.containsKey("سپردهٔ یک‌ساله"));
        List<?> insights = (List<?>) report.get("insights");
        assertTrue(insights.stream().anyMatch(line -> line.toString().contains("تورم رسمی")));
        assertTrue(insights.stream().noneMatch(line -> line.toString().contains("بخرید")), "never a recommendation");
        assertNotNull(((Map<?, ?>) report.get("deposit")).get("realYear"));
        assertEquals("+۱۲٫۳٪", MarketReportService.pct(12.34));
        assertEquals("منفی ۳۱٫۸٪", MarketReportService.signed(-31.84));
    }
}
