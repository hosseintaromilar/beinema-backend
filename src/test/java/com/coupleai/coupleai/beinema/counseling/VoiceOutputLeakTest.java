package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceOutputLeakTest {

    @Test
    void theQuestionTypeOfThePacketNeverReachesThePeople() {
        VoiceOutputParser.Result inText = VoiceOutputParser.parse("type=CHOOSE to OWNER (حسین)\nیه سؤال دارم");
        assertEquals("یه سؤال دارم", inText.text());
        VoiceOutputParser.Result inBlock = VoiceOutputParser.parse("[[QUESTION]]\ntype=CHOOSE\nکدوم راحت‌تره؟\n[[OPT|i1]] این\n[[OPT|i2]] اون\n[[/QUESTION]]");
        assertEquals("کدوم راحت‌تره؟", inBlock.questionText());
    }
}
