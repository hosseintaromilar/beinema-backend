package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectorOutputParserTest {

    @Test
    void readsAPlainObject() {
        DirectorOutputParser.Result result = DirectorOutputParser.parse("{\"schema\":\"director.v1\",\"decision\":{\"action\":\"REFLECT\"}}");
        assertTrue(result.ok());
        assertEquals("REFLECT", result.root().path("decision").path("action").asText());
    }

    @Test
    void toleratesFencesTextAroundAndTrailingCommas() {
        String raw = "```json\nHere it is:\n{\"schema\":\"director.v1\",\"decision\":{\"action\":\"ASK_OPEN\",},}\n```";
        DirectorOutputParser.Result result = DirectorOutputParser.parse(raw);
        assertTrue(result.ok(), result.error());
        assertEquals("ASK_OPEN", result.root().path("decision").path("action").asText());
    }

    @Test
    void reportsTheWorkflowFailureObject() {
        DirectorOutputParser.Result result = DirectorOutputParser.parse("{\"schema\":\"director.v1\",\"error\":\"DIRECTOR_FAILED\"}");
        assertFalse(result.ok());
        assertEquals("DIRECTOR_FAILED", result.error());
    }

    @Test
    void reportsBrokenOrIncompleteOutput() {
        assertTrue(DirectorOutputParser.parse("{\"decision\": {\"action\": ").error().startsWith("NO_JSON_OBJECT")
                || DirectorOutputParser.parse("{\"decision\": {\"action\": ").error().startsWith("INVALID_JSON"));
        assertEquals("NO_DECISION", DirectorOutputParser.parse("{\"schema\":\"director.v1\"}").error());
        assertEquals("EMPTY", DirectorOutputParser.parse("  ").error());
        assertEquals("NO_JSON_OBJECT", DirectorOutputParser.parse("ACTION=WAIT|ROUTE=SILENT").error());
    }
}
