package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonTeacherTest {

    @Test
    void theLibraryIsCompleteAndConsistent() {
        assertTrue(Lessons.all().size() >= 12);
        Set<String> ids = new HashSet<>();
        for (Lessons.Lesson lesson : Lessons.all()) {
            assertTrue(ids.add(lesson.id()), "unique id " + lesson.id());
            assertTrue(lesson.core().size() >= 3, lesson.id() + " has its core points");
            assertFalse(lesson.when().isBlank(), lesson.id() + " says when it helps");
            assertFalse(lesson.example().isBlank(), lesson.id() + " has an example");
            assertFalse(lesson.check().isBlank(), lesson.id() + " has a check question");
        }
        for (Lessons.Lesson lesson : Lessons.all()) {
            for (String related : lesson.related()) {
                assertTrue(ids.contains(related), lesson.id() + " points to a lesson that exists: " + related);
            }
        }
        // the topics the couple-work needs
        for (String id : Set.of("L-TOTAL-BEHAVIOR", "L-BEHAVIOR-CAR", "L-BASIC-NEEDS", "L-QUALITY-WORLD",
                "L-TEN-AXIOMS", "L-SEVEN-DEADLY", "L-SEVEN-CARING", "L-SOLVING-CIRCLE", "L-WDEP")) {
            assertTrue(Lessons.find(id).isPresent(), id);
        }
        assertTrue(Lessons.find("l-basic-needs").isPresent(), "ids are matched case-insensitively");
        assertTrue(Lessons.find("L-NOPE").isEmpty());
    }

    @Test
    void theTeachersLessonBecomesTheVoicesLessonBlock() {
        Lessons.Lesson lesson = Lessons.find("L-SEVEN-DEADLY").orElseThrow();
        String script = LessonTeacher.script("""
                {"schema":"lesson.v1","angle":"their nightly fights run on blame and nagging",
                 "opening":"چیزی که این چند وقت بین شما تکرار می‌شه، یه اسم آشنا داره.",
                 "points":["انتقاد و سرزنش معمولاً فاصله می‌سازن.","این عادت‌ها رو همه‌مون یاد گرفتیم."],
                 "example":"وقتی دیر می‌رسی و جمله‌ی اول «باز هم دیر کردی» باشه، شب به بحث می‌کشه.",
                 "check":"کدوم یکی از این عادت‌ها بیشتر سراغ خودت میاد؟"}
                """, lesson);

        assertTrue(script.startsWith("lesson=هفت عادت مخرب (L-SEVEN-DEADLY)"));
        assertTrue(script.contains("opening=چیزی که این چند وقت"));
        assertEquals(2, script.lines().filter(line -> line.startsWith("point=")).count());
        assertTrue(script.contains("check=کدوم یکی"));
    }

    @Test
    void anUnusableReplyIsRefusedAndTheLessonsOwnTextIsUsed() {
        Lessons.Lesson lesson = Lessons.find("L-BASIC-NEEDS").orElseThrow();
        assertNull(LessonTeacher.script("", lesson));
        assertNull(LessonTeacher.script("{\"schema\":\"lesson.v1\",\"error\":\"TEACHER_FAILED\"}", lesson));
        assertNull(LessonTeacher.script("{\"points\":[\"فقط یکی\"]}", lesson), "one point is not a lesson");

        String fallback = LessonTeacher.fallback(lesson);
        assertTrue(fallback.contains("point=" + lesson.core().get(0)));
        assertTrue(fallback.contains("check=" + lesson.check()));
    }
}
