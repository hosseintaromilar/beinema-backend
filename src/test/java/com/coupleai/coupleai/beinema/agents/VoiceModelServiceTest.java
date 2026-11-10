package com.coupleai.coupleai.beinema.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceModelServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void theVoiceNodeIsFoundByItsTitle() throws Exception {
        JsonNode workflow = JSON.readTree("{\"nodes\":[{\"title\":\"۱ - کارگردان | تحلیل\",\"cards\":[{\"config\":{\"provider\":{\"name\":\"deepseek\",\"model\":\"x\"}}}]},"
                + "{\"title\":\"۲ - صدای مشاور | نوشتن پیام فارسی\",\"cards\":[{\"config\":{\"provider\":{\"name\":\"anthropic\",\"model\":\"claude-haiku-5-5\"},\"args\":{}}}]}]}");
        assertEquals("claude-haiku-5-5", VoiceModelService.voiceConfig(workflow).path("provider").path("model").asText());
        assertThrows(IllegalArgumentException.class, () -> VoiceModelService.voiceConfig(JSON.readTree("{\"nodes\":[]}")));
    }

    @Test
    void eachProviderGetsSettingsItAccepts() {
        JsonNode claude = VoiceModelService.option("claude-sonnet-5-5").orElseThrow().args();
        assertFalse(claude.has("temperature"), "the newer Claude models refuse a temperature");
        assertEquals("low", claude.path("output_config").path("effort").asText());
        assertEquals("ephemeral", claude.path("cache_control").path("type").asText());
        JsonNode deepseek = VoiceModelService.option("deepseek-v4-1-flash-1017").orElseThrow().args();
        assertEquals("disabled", deepseek.path("thinking").path("type").asText());
        assertTrue(VoiceModelService.option("gpt-unknown").isEmpty());
    }
}
