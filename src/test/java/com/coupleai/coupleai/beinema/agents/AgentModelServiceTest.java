package com.coupleai.coupleai.beinema.agents;

import com.coupleai.coupleai.beinema.agents.AgentModelService.Part;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModelServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static AgentModelService.Option option(String model) {
        return AgentModelService.option(model).orElseThrow();
    }

    private static ObjectNode config(String provider, JsonNode args) {
        ObjectNode config = JSON.createObjectNode();
        config.putObject("provider").put("name", provider).put("model", "m");
        config.set("args", args);
        return config;
    }

    @Test
    void bothPartsAreFoundByTheirTitles() throws Exception {
        JsonNode workflow = JSON.readTree("{\"nodes\":["
                + "{\"title\":\"۱ - کارگردان | تحلیل وضعیت\",\"cards\":[{\"config\":{\"provider\":{\"name\":\"deepseek\",\"model\":\"d\"}}}]},"
                + "{\"title\":\"۲ - صدای مشاور | نوشتن پیام\",\"cards\":[{\"config\":{\"provider\":{\"name\":\"anthropic\",\"model\":\"v\"}}}]}]}");
        assertEquals("d", AgentModelService.config(workflow, Part.DIRECTOR).path("provider").path("model").asText());
        assertEquals("v", AgentModelService.config(workflow, Part.VOICE).path("provider").path("model").asText());
        assertThrows(IllegalArgumentException.class, () -> AgentModelService.config(JSON.readTree("{\"nodes\":[]}"), Part.VOICE));
    }

    @Test
    void theDirectorKeepsItsJsonAndThinkingIsSwitchedTheWayEachModelAccepts() {
        JsonNode deepseek = AgentModelService.args(Part.DIRECTOR, option("deepseek-v4-1-flash-1017"), true);
        assertEquals("json_object", deepseek.path("response_format").path("type").asText());
        assertEquals("enabled", deepseek.path("thinking").path("type").asText());

        JsonNode claudeDirector = AgentModelService.args(Part.DIRECTOR, option("claude-haiku-5-5"), false);
        assertFalse(claudeDirector.has("response_format"), "Claude refuses it; the prompt asks for JSON");
        assertFalse(claudeDirector.has("temperature"));
        assertEquals("disabled", claudeDirector.path("thinking").path("type").asText());

        JsonNode sonnetOff = AgentModelService.args(Part.VOICE, option("claude-sonnet-5-5"), false);
        assertEquals("between_tools", sonnetOff.path("thinking").path("type").asText(), "Sonnet 5.5 refuses disabled");
        JsonNode sonnetOn = AgentModelService.args(Part.VOICE, option("claude-sonnet-5-5"), true);
        assertEquals("low", sonnetOn.path("output_config").path("effort").asText());
        assertEquals("ephemeral", sonnetOn.path("cache_control").path("type").asText());
    }

    @Test
    void thinkingIsReadBackFromTheSettings() {
        assertTrue(AgentModelService.thinking(config("anthropic", AgentModelService.args(Part.VOICE, option("claude-haiku-5-5"), true))));
        assertFalse(AgentModelService.thinking(config("anthropic", AgentModelService.args(Part.VOICE, option("claude-sonnet-5-5"), false))));
        assertFalse(AgentModelService.thinking(config("deepseek", AgentModelService.args(Part.VOICE, option("deepseek-v4-pro"), false))));
        ObjectNode none = JSON.createObjectNode();
        none.putObject("response_format").put("type", "json_object");
        assertNull(AgentModelService.thinking(config("deepseek", none)), "nothing set: the model decides");
        assertFalse(option("grok-4.20-0309-non-reasoning").director(), "grok is for the voice only");
    }
}
