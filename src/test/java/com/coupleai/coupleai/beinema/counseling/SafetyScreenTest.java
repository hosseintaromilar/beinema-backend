package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyScreenTest {

    private static SafetyScreen.Result screen(String text) {
        return SafetyScreen.screen(List.of(new SafetyScreen.Input(1L, "OWNER", text)));
    }

    @Test
    void fearIsAMediumCueHoweverItIsTyped() {
        for (String text : List.of("راستش ازش می‌ترسم", "ازش می ترسم", "ازش میترسم", "ازش مي‌ترسم")) {
            SafetyScreen.Result result = screen(text);
            assertTrue(result.any(), text);
            assertFalse(result.high(), text);
            assertEquals("FEAR", result.cues().get(0).category());
        }
    }

    @Test
    void physicalViolenceAndSelfHarmAreHighCues() {
        assertTrue(screen("دیشب دوباره منو زد").high());
        assertTrue(screen("گاهی فکر خودکشی میاد سراغم").high());
        assertEquals("OWNER", screen("دیشب دوباره منو زد").cues().get(0).role());
    }

    @Test
    void ordinaryWordsAreNotCues() {
        assertFalse(screen("دیروز زنگ زد و گفت دیر میاد. خسته شدم از این وضع.").any());
        assertFalse(screen("").any());
    }
}
