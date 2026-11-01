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
}
