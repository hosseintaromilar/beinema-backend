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
        // a full reserve, real saving, no instalments: the figures allow growth, the quiz decides
        Map<String, Double> healthy = Map.of("INCOME", 50d, "ESSENTIAL", 20d, "OTHER", 5d, "CASH", 130d, "GOLD", 60d);
        FinanceKit.Plan plan = FinanceKit.plan(List.of(FinanceKit.risk(answers(2, 3))), healthy);
        assertEquals(FinanceKit.BALANCED, plan.profile());
        assertTrue(plan.steps().isEmpty());
        assertEquals(List.of(FinanceKit.CAUTIOUS, FinanceKit.BALANCED, FinanceKit.GROWTH),
                plan.scenarios().stream().map(FinanceKit.Scenario::level).toList());
        // a reserve of 3 months (60) stays aside: the rest of the cash and the gold
        assertEquals(130d, plan.investable(), 1e-9);
        assertTrue(plan.scenarios().get(2).badYear().contains("٪۲۵ تا ٪۳۵"), "a bold path shows its bad year");

        // capacity no higher than the profile: no bolder scenario
        FinanceKit.Plan careful = FinanceKit.plan(List.of(FinanceKit.risk(answers(3, 2))), healthy);
        assertEquals(List.of(FinanceKit.CAUTIOUS, FinanceKit.BALANCED),
                careful.scenarios().stream().map(FinanceKit.Scenario::level).toList());

        // little cash: the reserve comes first, and the figures hold the path back whatever the quiz says
        Map<String, Double> thin = Map.of("INCOME", 50d, "ESSENTIAL", 30d, "CASH", 40d, "GOLD", 60d);
        FinanceKit.Plan held = FinanceKit.plan(List.of(FinanceKit.risk(answers(2, 3))), thin);
        assertEquals("RESERVE", held.steps().get(0).id());
        assertEquals(FinanceKit.CAUTIOUS, held.profile());
        assertTrue(held.note().contains("کمتر از سه ماه"), held.note());
        assertEquals(List.of(FinanceKit.SAFE, FinanceKit.CAUTIOUS),
                held.scenarios().stream().map(FinanceKit.Scenario::level).toList());

        assertNull(FinanceKit.plan(List.of(), healthy));
    }

    @Test
    void theFiguresGiveTheCapacityAndADeadlineCapsTheRisk() {
        assertEquals(FinanceKit.SAFE, FinanceKit.picture(Map.of("INCOME", 30d, "ESSENTIAL", 35d, "CASH", 500d)).capacityLevel(),
                "spending more than income");
        assertEquals(FinanceKit.CAUTIOUS, FinanceKit.picture(Map.of("INCOME", 50d, "ESSENTIAL", 20d, "INSTALMENTS", 25d,
                "CASH", 100d)).capacityLevel(), "heavy instalments");
        assertNull(FinanceKit.picture(Map.of("CASH", 100d)).capacityLevel(), "unknown without income and costs");

        // bold quiz, healthy figures, but the money is needed in 10 months: the deadline decides
        Map<String, Double> v = new HashMap<>(Map.of("INCOME", 50d, "ESSENTIAL", 20d, "CASH", 200d,
                "GOAL_AMOUNT", 300d, "GOAL_MONTHS", 10d));
        FinanceKit.Plan plan = FinanceKit.plan(List.of(FinanceKit.risk(answers(3, 3))), v);
        assertEquals(FinanceKit.SAFE, plan.profile());
        assertTrue(plan.note().contains("۱۰ ماه"), plan.note());
    }

    @Test
    void aGoalOutOfReachGetsThreeHonestWaysNotMoreRisk() {
        // 100 now, 10 a month for 12 months = 220, goal 1000
        Map<String, Double> v = Map.of("INCOME", 40d, "ESSENTIAL", 25d, "OTHER", 5d, "CASH", 100d,
                "GOAL_AMOUNT", 1000d, "GOAL_MONTHS", 12d);
        FinanceKit.Goal goal = FinanceKit.picture(v).goal();
        assertEquals(220d, goal.projected(), 1e-9);
        assertFalse(goal.reachable());
        assertEquals(65d, goal.extraMonthly(), 1e-9);
        assertEquals(90, goal.monthsNeeded());
        FinanceKit.Plan plan = FinanceKit.plan(List.of(FinanceKit.risk(answers(3, 3))), v);
        assertTrue(plan.steps().stream().anyMatch(step -> "GOAL".equals(step.id())));

        assertTrue(FinanceKit.picture(Map.of("INCOME", 40d, "ESSENTIAL", 25d, "CASH", 100d,
                "GOAL_AMOUNT", 200d, "GOAL_MONTHS", 12d)).goal().reachable());
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
