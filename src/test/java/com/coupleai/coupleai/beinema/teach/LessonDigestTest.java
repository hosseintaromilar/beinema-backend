package com.coupleai.coupleai.beinema.teach;

import com.coupleai.coupleai.beinema.mirror.MirrorLetter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The nightly lesson block: only the reader's own cards, the practice once, and one reminder of an unanswered question. */
class LessonDigestTest {

    private static final String CONTENT = "{\"title\":\"رفتن یعنی رها کردن نیست\",\"truerBelief\":\"خداحافظی کوتاه مراقبت است.\","
            + "\"practice\":{\"title\":\"خداحافظی کوتاه\",\"steps\":[\"هر روز یک شکل خداحافظی کن.\"],"
            + "\"sources\":[{\"name\":\"NHS\",\"url\":\"https://www.nhs.uk/x\"},{\"name\":\"bad\",\"url\":\"javascript:alert(1)\"}]},"
            + "\"check\":{\"question\":\"کدام کمک می‌کند؟\",\"options\":[\"a\",\"b\",\"c\"]}}";

    private static TeachCard card(long id, Integer chosen) {
        TeachCard card = new TeachCard();
        card.setId(id);
        card.setUserId(7L);
        card.setTitle("رفتن یعنی رها کردن نیست");
        card.setContentJson(CONTENT);
        card.setChosen(chosen);
        return card;
    }

    @Test
    void theFirstEmailCarriesThePracticeAndTheReminderOnlyTheQuestion() {
        String first = LessonDigest.html(List.of(new LessonDigest.Due(card(1, null), false)), "https://beinema.ir/dashboard/lessons");
        assertTrue(first.contains("فقط برای خودت"));
        assertTrue(first.contains("هر روز یک شکل خداحافظی کن."));
        assertTrue(first.contains("https://www.nhs.uk/x"));
        assertFalse(first.contains("javascript:"), "only https sources are linked");
        assertTrue(first.contains("کدام کمک می‌کند؟"));
        assertTrue(first.contains("جواب دادن و دیدن درس"));

        String reminder = LessonDigest.html(List.of(new LessonDigest.Due(card(1, null), true)), "u");
        assertTrue(reminder.contains("هنوز به سؤال این درس جواب ندادی"));
        assertFalse(reminder.contains("هر روز یک شکل خداحافظی کن."), "the reminder is short");
        assertEquals("سؤال درس‌ات هنوز منتظر جوابه", LessonDigest.subject(List.of(new LessonDigest.Due(card(1, null), true))));
        assertEquals("درس امروزت در بین‌ما", LessonDigest.subject(List.of(new LessonDigest.Due(card(1, 0), false))));
    }

    @Test
    void anAnsweredCardAsksNothing() {
        String done = LessonDigest.html(List.of(new LessonDigest.Due(card(1, 2), false)), "u");
        assertFalse(done.contains("سؤال کوتاهش"));
        assertTrue(done.contains("دیدن درس‌های من"));
    }

    @Test
    void newCardsOfTheDayAndUnansweredOnesOfTheNightBefore() {
        TeachCardRepository repo = mock(TeachCardRepository.class);
        Instant cutoff = Instant.parse("2026-11-10T18:30:00Z");
        TeachCard today = card(1, null);
        TeachCard yesterday = card(2, null);
        when(repo.findAllByEmailedAtIsNullAndCreatedAtGreaterThanAndCreatedAtLessThanEqual(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(today));
        when(repo.findAllByEmailedAtLessThanEqualAndRemindedAtIsNullAndChosenIsNull(cutoff.minus(LessonDigest.REMIND_AFTER)))
                .thenReturn(List.of(yesterday));
        Map<Long, List<LessonDigest.Due>> due = new LessonDigest(repo).due(cutoff);
        assertEquals(2, due.get(7L).size());
        assertFalse(due.get(7L).get(0).reminder());
        assertTrue(due.get(7L).get(1).reminder());

        when(repo.findById(1L)).thenReturn(Optional.of(today));
        when(repo.findById(2L)).thenReturn(Optional.of(yesterday));
        Instant at = Instant.now();
        new LessonDigest(repo).sent(due.get(7L), at);
        assertEquals(at, today.getEmailedAt());
        assertNull(today.getRemindedAt());
        assertEquals(at, yesterday.getRemindedAt(), "recalled once, never again");
        assertNotNull(LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault()));
    }

    @Test
    void theMirrorEmailHoldsTheLessonsBeforeItsPrivacyNote() {
        MirrorLetter.Content c = new MirrorLetter.Content("حسین", "گفتگو", "۱۹ آبان", "same", "مثل قبل", "", "", "", "",
                List.of(), List.of(), "", "https://beinema.ir/chat-rooms/1", "https://beinema.ir/stop");
        String html = MirrorLetter.html(c, "<div>LESSONS</div>");
        assertTrue(html.indexOf("LESSONS") > 0 && html.indexOf("LESSONS") < html.indexOf("🔒"));
        assertEquals(MirrorLetter.html(c, ""), MirrorLetter.html(c));
    }
}
