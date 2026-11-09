package com.coupleai.coupleai.beinema.mirror;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MirrorReportTest {

    @Test
    void theDayClosesAtTenAtNight() {
        assertEquals(LocalDateTime.of(2026, 11, 8, 22, 0),
                MirrorReportService.lastCutoff(LocalDateTime.of(2026, 11, 9, 21, 59)), "before 22:00: last night's");
        assertEquals(LocalDateTime.of(2026, 11, 9, 22, 0),
                MirrorReportService.lastCutoff(LocalDateTime.of(2026, 11, 9, 22, 0)), "at 22:00: tonight's");
        assertEquals(LocalDateTime.of(2026, 11, 9, 22, 0),
                MirrorReportService.lastCutoff(LocalDateTime.of(2026, 11, 10, 1, 30)), "after midnight: still last night's");
    }

    @Test
    void persianDate() {
        assertEquals("۱۸ آبان ۱۴۰۵", MirrorReportService.jalali(LocalDate.of(2026, 11, 9)));
    }

    @Test
    void theEmailSaysWhoCanReadItAndEscapesTheWords() {
        MirrorLetter.Content content = new MirrorLetter.Content("سارا", "حرف‌های <ما>", "۱۸ آبان ۱۴۰۵", "softer",
                "نرم‌تر شده", "چرخهٔ قبلی کمی آرام‌تر شده است.", "امروز بهتر شنیدیم.", "دربارهٔ خرج‌ها حرف زدیم.",
                "", List.of("احترام"), List.of(), "هفته‌ای یک بار با هم حساب کنیم.",
                "https://beinema.ir/chat-rooms/5?mirror=1", "https://beinema.ir/api/email/mirror-off?u=1&t=x");
        String html = MirrorLetter.html(content);
        assertTrue(html.contains(MirrorLetter.PRIVACY));
        assertTrue(html.contains("حرف‌های &lt;ما&gt;"));
        assertFalse(html.contains("<ما>"));
        assertTrue(html.contains("mirror-off"));
        assertFalse(html.contains("جلسهٔ قبل"), "an empty part is left out");
        String text = MirrorLetter.text(content);
        assertTrue(text.contains("قدم کوچک بعدی: هفته‌ای یک بار با هم حساب کنیم."));
        assertTrue(text.contains(MirrorLetter.PRIVACY));
    }
}
