package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.counseling.DecisionValidator;
import com.coupleai.coupleai.beinema.counseling.DirectorDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The budget book: Iranian months, totals with saving apart, the average of the months before, and what Pouria may write. */
class BudgetTest {

    private static BudgetEntry line(String category, long amount, LocalDate day) {
        BudgetEntry entry = new BudgetEntry();
        BudgetKit.Category c = BudgetKit.category(category);
        entry.setCategory(category);
        entry.setKind(c.kind());
        entry.setAmount(amount);
        entry.setDay(day);
        return entry;
    }

    @Test
    void monthsAreIranian() {
        assertEquals("1405/08", BudgetKit.month(LocalDate.of(2026, 11, 17)));
        assertEquals("آبان ۱۴۰۵", BudgetKit.monthName("1405/08"));
        assertEquals("1404/12", BudgetKit.shift("1405/01", -1));
        assertEquals("1406/01", BudgetKit.shift("1405/12", 1));
        assertTrue(BudgetKit.valid("1405/08"));
        assertFalse(BudgetKit.valid("1405/13"));
    }

    @Test
    void savingIsKeptApartFromSpending() {
        BudgetService.Totals totals = BudgetService.totals(List.of(
                line("SALARY", 50_000_000, LocalDate.of(2026, 11, 1)),
                line("FOOD", 12_000_000, LocalDate.of(2026, 11, 2)),
                line("SAVING", 8_000_000, LocalDate.of(2026, 11, 3))));
        assertEquals(50_000_000, totals.income());
        assertEquals(12_000_000, totals.spending());
        assertEquals(8_000_000, totals.saved());
    }

    @Test
    void theAverageIsOfTheMonthsBeforeThatHaveLines() {
        // Mehr and Shahrivar 1405 have lines; Aban is the current month and is left out
        List<BudgetEntry> all = List.of(
                line("SALARY", 40_000_000, LocalDate.of(2026, 10, 5)),
                line("FOOD", 10_000_000, LocalDate.of(2026, 10, 6)),
                line("SALARY", 60_000_000, LocalDate.of(2026, 9, 5)),
                line("FOOD", 20_000_000, LocalDate.of(2026, 9, 6)),
                line("SALARY", 99_000_000, LocalDate.of(2026, 11, 5)));
        Map<String, Object> average = BudgetService.average(all, "1405/08");
        assertEquals(2, average.get("months"));
        assertEquals(50_000_000L, average.get("income"));
        assertEquals(15_000_000L, average.get("spending"));
        assertNull(BudgetService.average(List.of(), "1405/08"));
    }

    @Test
    void pouriaWritesOnlyWholeLinesAndLimitsOfSpending() throws Exception {
        DirectorDecision d = new DirectorDecision();
        DecisionValidator.readFinance(new ObjectMapper().readTree("""
                {"card": "BUDGET",
                 "budget": [{"category": "food", "amount": 3.5, "days_ago": 2, "note": "خرید هفته"},
                            {"category": "NOPE", "amount": 1},
                            {"category": "FUN", "amount": 0}],
                 "limits": {"FOOD": 12, "SALARY": 50, "FUN": 0}}"""), d);
        assertEquals("BUDGET", d.financeCard);
        assertEquals(1, d.financeBudget.size());
        assertEquals("FOOD", d.financeBudget.get(0).category());
        assertEquals(2, d.financeBudget.get(0).daysAgo());
        assertEquals(Map.of("FOOD", 12.0, "FUN", 0.0), d.financeLimits, "a limit is only for spending");
    }
}
