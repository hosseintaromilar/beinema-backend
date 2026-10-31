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

    @Test
    void redLineAgainstRedLineIsTheOnlyCoreGap() {
        assertEquals(ElikaKit.CORE_GAP, ElikaKit.scaleStatus(0, 0, 3, 0));
        assertEquals(ElikaKit.WORKABLE, ElikaKit.scaleStatus(0, 0, 3, 1));
        assertEquals(ElikaKit.ALIGNED, ElikaKit.scaleStatus(0, 0, 3, 2));
        assertEquals(ElikaKit.ALIGNED, ElikaKit.scaleStatus(1, 1, 2, 1));
        assertEquals(ElikaKit.ALIGNED, ElikaKit.scaleStatus(2, 0, 2, 0));
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
