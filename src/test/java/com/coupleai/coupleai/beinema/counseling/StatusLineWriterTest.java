package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class StatusLineWriterTest {

    private final StatusLineWriter writer = new StatusLineWriter(
            mock(com.coupleai.coupleai.beinema.chat.ChatRoomEventHub.class), new ObjectMapper(),
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

    @Test
    void theEarlyStickerComesWithTheLineAndOnlyAKnownGentleOne() throws Exception {
        Map<String, String> both = writer.parse("{\"thinking\": \"دارم به حرفت فکر می‌کنم\", \"react\": \"beside\"}");
        assertEquals("BESIDE", both.get("react"));
        assertNull(writer.parse("{\"react\": \"BYE\"}"), "a goodbye or a question sticker is not an early one");
        assertEquals("LISTEN", writer.parse("{\"thinking\": \"\", \"react\": \"LISTEN\"}").get("react"));
        assertNull(writer.parse("{\"react\": \"NONE\"}"));
    }

    @Test
    void whoeverClaimsFirstWins() {
        StatusLineWriter.Early early = new StatusLineWriter.Early(5L, java.util.List.of());
        assertEquals(true, early.claim());
        assertEquals(true, early.decide(), "the sticker is already there: the director adds none");

        StatusLineWriter.Early late = new StatusLineWriter.Early(5L, java.util.List.of());
        assertEquals(false, late.decide(), "decided before the sticker came");
        assertEquals(false, late.claim(), "so the late sticker is not put on");
    }
}
