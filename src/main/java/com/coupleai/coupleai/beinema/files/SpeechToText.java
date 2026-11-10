package com.coupleai.coupleai.beinema.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * A recorded voice message written down word for word, in Persian, by Gemini through Metis. Only the
 * words: no summary, no reading of feelings. The person sees the text before sending and can fix it.
 */
@Service
public class SpeechToText {

    private static final Logger log = LoggerFactory.getLogger(SpeechToText.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String PROMPT = """
            Transcribe this voice message word for word. It is usually Persian (Farsi), spoken casually; \
            write it in Persian script exactly as said, in spoken style, with natural punctuation. \
            Words said in another language stay in that language. Do not translate, summarize, correct or add anything. \
            If nothing intelligible is said, output exactly: -
            Output only the transcript.""";

    private final String key;
    private final String base;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public SpeechToText(@Value("${metis.api-key:}") String key,
                        @Value("${metis.base-url:https://api.metisai.ir}") String base,
                        @Value("${beinema.voice.stt-model:gemini-2.5-flash}") String model) {
        this.key = key;
        this.base = base.replaceAll("/+$", "");
        this.model = model;
    }

    /** The words of the recording; empty when nothing could be heard or the call failed. */
    public Optional<String> transcribe(byte[] audio, String contentType) {
        ObjectNode body = JSON.createObjectNode();
        var parts = body.putArray("contents").addObject().put("role", "user").putArray("parts");
        parts.addObject().putObject("inline_data")
                .put("mime_type", contentType.replaceAll(";.*", ""))
                .put("data", Base64.getEncoder().encodeToString(audio));
        parts.addObject().put("text", PROMPT);
        ObjectNode generation = body.putObject("generationConfig");
        generation.put("temperature", 0);
        generation.putObject("thinkingConfig").put("thinkingBudget", 0);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/v1beta/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(60))
                .header("x-goog-api-key", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Speech to text refused ({}): {}", response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
                return Optional.empty();
            }
            return text(JSON.readTree(response.body()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception failure) {
            log.warn("Speech to text failed: {}", failure.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> text(JsonNode response) {
        StringBuilder out = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            out.append(part.path("text").asText(""));
        }
        String text = out.toString().strip();
        return text.isEmpty() || "-".equals(text) ? Optional.empty() : Optional.of(text);
    }
}
