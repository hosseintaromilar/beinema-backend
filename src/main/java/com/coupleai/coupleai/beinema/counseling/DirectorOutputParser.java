package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Reads the director's raw reply into JSON. Tolerates code fences, text around
 * the object and trailing commas; anything else is reported as an error so the
 * orchestrator can ask once for a repair and otherwise keep the old memory.
 */
public final class DirectorOutputParser {

    public static final String SCHEMA = "director.v1";

    private static final ObjectMapper LENIENT = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build();

    public record Result(JsonNode root, String error) {
        public boolean ok() {
            return root != null && error == null;
        }
    }

    private DirectorOutputParser() {
    }

    public static Result parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Result(null, "EMPTY");
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            text = firstLine < 0 ? "" : text.substring(firstLine + 1);
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return new Result(null, "NO_JSON_OBJECT");
        }
        try {
            JsonNode root = LENIENT.readTree(text.substring(start, end + 1));
            if (root == null || !root.isObject()) {
                return new Result(null, "NOT_AN_OBJECT");
            }
            String error = root.path("error").asText("");
            if (!error.isBlank()) {
                return new Result(null, error);
            }
            if (!root.has("decision")) {
                return new Result(null, "NO_DECISION");
            }
            return new Result(root, null);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            return new Result(null, "INVALID_JSON: " + message.substring(0, Math.min(180, message.length())));
        }
    }
}
