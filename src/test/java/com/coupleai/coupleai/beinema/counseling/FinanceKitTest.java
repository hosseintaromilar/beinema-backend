package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceKitTest {

    private static Map<String, Integer> answers(int will, int cap) {
        Map<String, Integer> out = new HashMap<>();
        FinanceKit.RISK_ITEMS.forEach(item -> out.put(item.id(), FinanceKit.WILL.equals(item.dim()) ? will : cap));
        return out;
    }

    @Test
    void theProfileIsTheLowerOfWillingnessAndCapacity() {
        FinanceKit.Risk bold = FinanceKit.risk(answers(3, 3));
        assertEquals(100, bold.willingness());
        assertEquals(FinanceKit.GROWTH, bold.profile());

        // likes risk, but life cannot bear a loss: capacity decides
        FinanceKit.Risk limited = FinanceKit.risk(answers(3, 0));
        assertEquals(FinanceKit.GROWTH, limited.willingnessLevel());
        assertEquals(FinanceKit.SAFE, limited.profile());

        assertEquals(FinanceKit.BALANCED, FinanceKit.risk(answers(2, 2)).profile());
        assertEquals(FinanceKit.CAUTIOUS, FinanceKit.risk(answers(1, 2)).profile());
    }

    @Test
    void sayingOneThingAndDoingAnotherIsFlagged() {
        Map<String, Integer> a = answers(2, 2);
        a.put("W_DROP", 0);     // would sell everything after a fall
        a.put("W_CHOICE", 3);   // but picks the biggest gamble
        assertTrue(FinanceKit.risk(a).mismatch());
        assertFalse(FinanceKit.risk(answers(2, 2)).mismatch());
    }

    @Test
    void aQuizIsCompleteOnlyWithEveryAnswerInRange() {
        Map<String, Integer> a = answers(1, 1);
        assertTrue(FinanceKit.complete(a));
        a.put("C_LOSS", 4);
        assertFalse(FinanceKit.complete(a));
        a.remove("C_LOSS");
        assertFalse(FinanceKit.complete(a));
    }

    @Test
    void thePictureGivesTheRatiosFromTheirFigures() {
        FinanceKit.Picture p = FinanceKit.picture(Map.of("INCOME", 50d, "ESSENTIAL", 25d, "OTHER", 10d, "INSTALMENTS", 5d,
                "CASH", 100d, "GOLD", 100d, "PROPERTY", 3000d, "DEBTS", 200d));
        assertEquals(10d, p.surplus());
        assertEquals(0.2, p.savingRate(), 1e-9);
        assertEquals(0.1, p.instalmentShare(), 1e-9);
        assertEquals(4d, p.reserveMonths());
        assertEquals(3000d, p.netWorth());
        assertEquals(50d, p.mix().get("GOLD"), 1e-9);

        FinanceKit.Picture empty = FinanceKit.picture(Map.of());
        assertNull(empty.surplus());
        assertNull(empty.reserveMonths());
    }

    @Test
    void thePlanPutsTheReserveFirstAndOffersABolderStepOnlyWhenTheyCanBearIt() {
        Map<String, Double> v = Map.of("INCOME", 50d, "ESSENTIAL", 30d, "CASH", 40d, "GOLD", 60d);
        FinanceKit.Plan plan = FinanceKit.plan(List.of(FinanceKit.risk(answers(2, 3))), v);
        assertEquals(FinanceKit.BALANCED, plan.profile());
        assertEquals("RESERVE", plan.steps().get(0).id());
        assertEquals(List.of(FinanceKit.CAUTIOUS, FinanceKit.BALANCED, FinanceKit.GROWTH),
                plan.scenarios().stream().map(FinanceKit.Scenario::level).toList());
        // reserve of 3 months (90) is more than the cash: only the gold is there to invest
        assertEquals(60d, plan.investable(), 1e-9);

        // capacity no higher than the profile: no bolder scenario
        FinanceKit.Plan careful = FinanceKit.plan(List.of(FinanceKit.risk(answers(3, 2))), v);
        assertEquals(List.of(FinanceKit.CAUTIOUS, FinanceKit.BALANCED),
                careful.scenarios().stream().map(FinanceKit.Scenario::level).toList());

        assertNull(FinanceKit.plan(List.of(), v));
    }

    @Test
    void forTwoPeopleTheMoreCarefulProfileDecidesSharedMoney() {
        FinanceKit.Plan plan = FinanceKit.plan(List.of(FinanceKit.risk(answers(3, 3)), FinanceKit.risk(answers(1, 1))), Map.of());
        assertEquals(FinanceKit.CAUTIOUS, plan.profile());
        assertFalse(plan.note().isBlank());
    }

    @Test
    void theDirectorsCardAndFiguresAreReadAndUnknownOnesDropped() throws Exception {
        DirectorDecision d = new DirectorDecision();
        DecisionValidator.readFinance(new ObjectMapper().readTree(
                "{\"card\":\"picture\",\"hints\":{\"INCOME\":45,\"SALARY\":10,\"CASH\":-3}}"), d);
        assertEquals("PICTURE", d.financeCard);
        assertEquals(Map.of("INCOME", 45d), d.financeHints);

        DirectorDecision none = new DirectorDecision();
        DecisionValidator.readFinance(new ObjectMapper().readTree("{\"card\":\"STOCKS\"}"), none);
        assertNull(none.financeCard);
    }

    @Test
    void formNumbersMayBePersianDigits() {
        assertEquals(1250.5, FinanceService.number("۱٬۲۵۰٫۵"));
        assertEquals(45d, FinanceService.number("45"));
        assertNull(FinanceService.number(""));
    }
}
