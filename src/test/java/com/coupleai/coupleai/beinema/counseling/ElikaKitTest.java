package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElikaKitTest {

    private static Map<String, Integer> same(ElikaKit.Test test, int answer, int firmness) {
        Map<String, Integer> out = new HashMap<>();
        test.items().forEach(item -> {
            out.put(item.id(), answer);
            if (test.scale()) {
                out.put(item.id() + ElikaKit.FLEX_SUFFIX, firmness);
            }
        });
        return out;
    }

    /** The calmest answer to every statement, whichever way it is worded. */
    private static Map<String, Integer> healthy(ElikaKit.Test test) {
        Map<String, Integer> out = new HashMap<>();
        test.items().forEach(item -> out.put(item.id(), item.reverse() ? 3 : 0));
        return out;
    }

    @Test
    void redLineAgainstRedLineIsTheOnlyCoreGap() {
        assertEquals(ElikaKit.CORE_GAP, ElikaKit.scaleStatus(0, 0, 3, 0));
        assertEquals(ElikaKit.TENSION, ElikaKit.scaleStatus(0, 0, 3, 1), "a red line against a far answer: a potential conflict");
        assertEquals(ElikaKit.ALIGNED, ElikaKit.scaleStatus(0, 0, 3, 2));
        assertEquals(ElikaKit.ALIGNED, ElikaKit.scaleStatus(1, 1, 2, 1));
        assertEquals(ElikaKit.ALIGNED, ElikaKit.scaleStatus(2, 0, 2, 0));
    }

    @Test
    void aCoreSubjectIsOneStepGraver() {
        // children: one red line against a far answer is a likely mismatch, not a tension
        assertEquals(ElikaKit.CORE_GAP, ElikaKit.scaleStatus(0, 0, 3, 1, true));
        assertEquals(ElikaKit.TENSION, ElikaKit.scaleStatus(0, 1, 3, 1, true));
        assertEquals(ElikaKit.WORKABLE, ElikaKit.scaleStatus(0, 1, 3, 1, false));
        assertTrue(ElikaKit.test("GOALS").orElseThrow().item("CHILDREN").orElseThrow().core());
        assertFalse(ElikaKit.test("GOALS").orElseThrow().item("MONEY").orElseThrow().core());
    }

    @Test
    void theDossierNeverAddsUpAndSaysHowReadyTheyAreNotYesOrNo() {
        ElikaKit.Test goals = ElikaKit.test("GOALS").orElseThrow();
        ElikaKit.Test daily = ElikaKit.test("DAILY").orElseThrow();
        ElikaKit.Test conflict = ElikaKit.test("CONFLICT").orElseThrow();
        Map<String, ElikaKit.Comparison> done = new java.util.LinkedHashMap<>();
        done.put("GOALS", ElikaKit.compare(goals, same(goals, 1, 1), same(goals, 1, 1)));
        ElikaKit.Dossier few = ElikaKit.dossier(done);
        assertEquals(ElikaKit.EXPLORE, few.readiness(), "one test is not enough to decide");
        assertEquals(3, few.unknown().size());

        done.put("DAILY", ElikaKit.compare(daily, same(daily, 1, 1), same(daily, 1, 1)));
        done.put("CONFLICT", ElikaKit.compare(conflict, healthy(conflict), healthy(conflict)));
        assertEquals(ElikaKit.READY, ElikaKit.dossier(done).readiness());

        // agreeing on everything else does not make up for a gap about children
        Map<String, Integer> mine = same(goals, 1, 1);
        mine.put("CHILDREN", 0);
        mine.put("CHILDREN" + ElikaKit.FLEX_SUFFIX, 0);
        Map<String, Integer> other = same(goals, 1, 1);
        other.put("CHILDREN", 3);
        done.put("GOALS", ElikaKit.compare(goals, mine, other));
        ElikaKit.Dossier split = ElikaKit.dossier(done);
        assertEquals(ElikaKit.RESOLVE, split.readiness());
        assertEquals(1, split.groups().get(ElikaKit.CORE_GAP).size());

        // contempt in the way they argue: a specialist too
        Map<String, Integer> harsh = healthy(conflict);
        harsh.put("CONTEMPT", 2);
        done.put("CONFLICT", ElikaKit.compare(conflict, harsh, healthy(conflict)));
        assertEquals(ElikaKit.SPECIALIST, ElikaKit.dossier(done).readiness());
    }

    @Test
    void goalsVerdictFollowsTheGaps() {
        ElikaKit.Test goals = ElikaKit.test("GOALS").orElseThrow();
        assertEquals("SAME", ElikaKit.compare(goals, same(goals, 1, 1), same(goals, 1, 1)).level());
        Map<String, Integer> other = same(goals, 1, 0);
        other.put("CHILDREN", 3);
        Map<String, Integer> mine = same(goals, 1, 0);
        mine.put("CHILDREN", 0);
        assertEquals("SPLIT", ElikaKit.compare(goals, mine, other).level());
    }

    @Test
    void theFearAnswerNeverReachesTheResult() {
        ElikaKit.Test conflict = ElikaKit.test("CONFLICT").orElseThrow();
        Map<String, Integer> afraid = same(conflict, 0, 0);
        afraid.put("FEAR", 3);
        ElikaKit.Comparison result = ElikaKit.compare(conflict, afraid, same(conflict, 0, 0));
        assertFalse(result.rows().stream().anyMatch(row -> row.id().equals("FEAR")));
    }

    @Test
    void reversedStatementsCountTheOtherWay() {
        ElikaKit.Test conflict = ElikaKit.test("CONFLICT").orElseThrow();
        ElikaKit.Item repair = conflict.item("REPAIR").orElseThrow();
        assertEquals(3, ElikaKit.risk(repair, 0));
        assertEquals(0, ElikaKit.risk(repair, 3));
    }

    @Test
    void contemptIsSeriousEarlierThanTheRest() {
        ElikaKit.Test conflict = ElikaKit.test("CONFLICT").orElseThrow();
        Map<String, Integer> first = same(conflict, 0, 0);
        first.put("REPAIR", 3);
        first.put("RESOLVE", 3);
        Map<String, Integer> second = new HashMap<>(first);
        first.put("CONTEMPT", 2);
        assertEquals("SPLIT", ElikaKit.compare(conflict, first, second).level());
    }

    @Test
    void anAnswerMissingOrOutOfRangeIsNotComplete() {
        ElikaKit.Test goals = ElikaKit.test("GOALS").orElseThrow();
        Map<String, Integer> answers = same(goals, 1, 1);
        assertTrue(ElikaKit.complete(goals, answers));
        answers.put("CHILDREN", 9);
        assertFalse(ElikaKit.complete(goals, answers));
    }

    @Test
    void everyLessonHasACardAndIsFoundThroughLessons() {
        ElikaKit.lessons().forEach(lesson -> {
            assertTrue(ElikaKit.lessonCard(lesson.id()).isPresent());
            assertEquals(lesson, Lessons.find(lesson.id()).orElseThrow());
        });
        assertFalse(Lessons.all().stream().anyMatch(lesson -> lesson.id().startsWith(ElikaKit.LESSON_PREFIX)));
    }
}
