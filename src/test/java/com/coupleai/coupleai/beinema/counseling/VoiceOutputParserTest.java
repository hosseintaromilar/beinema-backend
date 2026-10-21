package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceOutputParserTest {

    @Test
    void plainWordsStayAsTheyAre() {
        VoiceOutputParser.Result result = VoiceOutputParser.parse("یه لحظه بمون.\n[[BREAK|PAUSE=1]]\nچی شد که این حس رو داشتی؟");
        assertFalse(result.failed());
        assertFalse(result.questionBlock());
        assertTrue(result.text().contains("[[BREAK|PAUSE=1]]"), "BREAK markers are delivered as before");
    }

    @Test
    void theQuestionBlockIsSeparatedFromTheLeadIn() {
        String raw = "می‌خوام مطمئن شم درست فهمیدم.\n"
                + "[[QUESTION]]\n"
                + "وقتی می‌گی دیگه نمی‌خوای هر شب بحث کنید، کدوم نزدیک‌تره؟\n"
                + "[[OPT|i45]] می‌خوام دعواها کمتر بشن\n"
                + "[[OPT|i46]] می‌خوام حسین رفتارش رو عوض کنه\n"
                + "[[OPT|i47]] خسته‌ام و درباره‌ی ادامه تردید دارم\n"
                + "[[/QUESTION]]";
        VoiceOutputParser.Result result = VoiceOutputParser.parse(raw);

        assertTrue(result.questionBlock());
        assertEquals("می‌خوام مطمئن شم درست فهمیدم.", result.text());
        assertTrue(result.questionText().startsWith("وقتی می‌گی"));
        assertEquals(List.of("i45", "i46", "i47"), List.copyOf(result.options().keySet()));
        assertEquals("می‌خوام دعواها کمتر بشن", result.options().get("i45"));
    }

    @Test
    void aConfirmBlockHasOnlyTheQuestion() {
        VoiceOutputParser.Result result = VoiceOutputParser.parse(
                "[[QUESTION]]\nمنظورت اینه که از تکرار هرروزه خسته شدی؟\n[[/QUESTION]]");
        assertTrue(result.questionBlock());
        assertEquals("", result.text());
        assertTrue(result.options().isEmpty());
    }

    @Test
    void anErrorFromTheWorkflowIsReported() {
        VoiceOutputParser.Result result = VoiceOutputParser.parse("[[ERROR|VOICE_FAILED]]");
        assertTrue(result.failed());
        assertEquals("VOICE_FAILED", result.error());
    }

    @Test
    void aStrayGovernanceLineNeverReachesTheCouple() {
        VoiceOutputParser.Result result = VoiceOutputParser.parse("[[GOV|GOV=NONE|TARGET=NONE|DURATION=0]]\nسلام دوباره.");
        assertEquals("سلام دوباره.", result.text());
    }
}
