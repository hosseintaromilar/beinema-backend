package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceGuardTest {

    @Test
    void writtenPersianIsFoundAndSpokenIsLeftAlone() {
        assertEquals("است", VoiceGuard.written("کار ما این است که حد پیدا کنیم").orElseThrow());
        assertEquals("را", VoiceGuard.written("این حرف را بگو").orElseThrow());
        assertTrue(VoiceGuard.written("ببین، راستش این کار خوب نیست؛ رابطه‌تون مهمه").isEmpty());
    }

    @Test
    void sayingNearlyTheSameAgainIsARepeat() {
        String first = "کار ما اینجا اینه که یه حد مشخص با خانواده‌ها پیدا کنیم و جمله‌اش رو بسازیم";
        assertTrue(VoiceGuard.repeats("کار ما اینجا اینه که یه حد مشخص با خانواده‌ها پیدا کنیم و جمله‌اش رو بسازیم الف", List.of(first)));
        assertFalse(VoiceGuard.repeats("درآمد ماهانه‌ت الان تقریباً چقدره و قسط چقدر داری؟", List.of(first)));
        assertFalse(VoiceGuard.repeats("باشه", List.of("باشه")));
    }

    @Test
    void adviceIsFoundOnlyOutsideQuestions() {
        assertTrue(VoiceGuard.advises("می‌فهمم سخته. پیشنهاد می‌کنم از فردا شب یه ربع با هم قدم بزنید.").isPresent());
        assertTrue(VoiceGuard.advises("بهتره اول خودت آروم بشی").isPresent());
        assertTrue(VoiceGuard.advises("می‌تونی امشب بهش بگی چی ناراحتت کرده.").isPresent());
        assertTrue(VoiceGuard.advises("می‌تونی بگی از کی این‌طوری شده؟").isEmpty(), "a question is not advice");
        assertTrue(VoiceGuard.advises("حس می‌کنم پشتش یه خستگی قدیمیه. درسته؟").isEmpty());
    }

    @Test
    void adviceSentencesAreCutAndTheRestStays() {
        String kept = VoiceGuard.withoutAdvice("می‌فهمم چقدر خسته‌ای. بهتره امشب باهاش حرف بزنی. از کی این‌طوری شده؟");
        assertEquals("می‌فهمم چقدر خسته‌ای. از کی این‌طوری شده؟", kept);
    }

    @Test
    void beforeACardOnlyTheLeadInStays() {
        assertEquals("گفتنش راحت نیست.", VoiceGuard.withoutClosingQuestions("گفتنش راحت نیست. وقتی زنگ می‌زنی، کدوم به تو نزدیک‌تره؟"));
        assertEquals("", VoiceGuard.withoutClosingQuestions("چی برات مهمه؟ کی شروع شد؟"));
        assertEquals("فهمیدم. بیا ببینیم.", VoiceGuard.withoutClosingQuestions("فهمیدم. بیا ببینیم."));
    }

    @Test
    void aLeadInThatOnlySaysWhatTheCardAsksGoes() {
        String card = "وقتی دیر می‌کنه و چند بار زنگ می‌زنی، این زنگ‌ها بیشتر برای چیه؟";
        assertEquals("ممنون که اینو گفتی، گفتنش راحت نیست.", VoiceGuard.leadInFor("ممنون که اینو گفتی، گفتنش راحت نیست. می‌خوام ببینم زنگ‌هایی که می‌زنی بیشتر برای چیه.", card));
        assertEquals("گفتنش راحت نبود.", VoiceGuard.leadInFor("گفتنش راحت نبود.", card), "a lead-in about something else stays");
        assertEquals("", VoiceGuard.leadInFor("افسانه، می‌خوام دقیق‌تر بفهمم این شب‌های دیروقت تو خودت چی می‌خوای.", "تو توی این شب‌هایی که دیر میاد خونه، خودت دقیقاً چی می‌خوای؟"));
    }
}
