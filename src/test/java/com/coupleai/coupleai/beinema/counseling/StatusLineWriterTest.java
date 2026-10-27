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
    void bothLinesComeCleanWithoutQuotesOrDots() throws Exception {
        Map<String, String> lines = writer.parse("```json\n{\"thinking\": \"«دارم به حرفت درباره‌ی دعوای دیشب فکر می‌کنم»…\", "
                + "\"writing\": \"دارم جوابمو آماده می‌کنم.\"}\n```");
        assertEquals("دارم به حرفت درباره‌ی دعوای دیشب فکر می‌کنم", lines.get("thinking"));
        assertEquals("دارم جوابمو آماده می‌کنم", lines.get("writing"));
    }

    @Test
    void aMissingOrTooLongLineIsNotShown() throws Exception {
        assertNull(writer.parse("{\"thinking\": \"دارم فکر می‌کنم\"}"));
        assertNull(writer.parse("{\"thinking\": \"" + "خیلی ".repeat(30) + "\", \"writing\": \"دارم می‌نویسم\"}"));
    }
}
