package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class StatusLineWriterTest {

    private final StatusLineWriter writer = new StatusLineWriter(
            mock(com.coupleai.coupleai.beinema.Service.ChatRoomEventHub.class), new ObjectMapper(),
            "https://example.invalid", "key", "model");

    @Test
    void theLineComesCleanWithoutQuotesOrDots() throws Exception {
        Map<String, String> lines = writer.parse("```json\n{\"thinking\": \"«دارم به حرفت درباره‌ی دعوای دیشب فکر می‌کنم»…\"}\n```");
        assertEquals("دارم به حرفت درباره‌ی دعوای دیشب فکر می‌کنم", lines.get("thinking"));
    }

    @Test
    void onlyACardOrALessonIsAnnounced() {
        DirectorDecision d = new DirectorDecision();
        d.action = "ASK_CLARIFYING";
        assertEquals("question", TurnOrchestrator.writingPhase(d));
        d.action = "TEACH";
        assertEquals("lesson", TurnOrchestrator.writingPhase(d));
        d.action = "REFLECT";
        assertEquals("writing", TurnOrchestrator.writingPhase(d));
        d.action = "ASK_OPEN";
        assertEquals("writing", TurnOrchestrator.writingPhase(d), "the voice may still answer first");
    }

    @Test
    void aMissingOrTooLongLineIsNotShown() throws Exception {
        assertNull(writer.parse("{}"));
        assertNull(writer.parse("{\"thinking\": \"" + "خیلی ".repeat(30) + "\"}"));
    }
}
