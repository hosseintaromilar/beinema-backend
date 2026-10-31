package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The short line the page shows while the counselor thinks about a message,
 * tied to what was written («دارم به حرفت دربارهٔ دیر اومدن‌ها فکر می‌کنم»).
 * A small fast model writes it next to the real turn; if it is slow or fails,
 * the page keeps its plain line. It never answers, judges or advises, and it
 * promises nothing about the reply: the next move is not decided yet.
 */
@Component
public class StatusLineWriter {

    private static final Logger log = LoggerFactory.getLogger(StatusLineWriter.class);
    static final int MAX_LINE = 70;
    private static final int MAX_INPUT = 700;

    private static final String PROMPT = """
            You write one tiny status line that a Persian companion shows on screen while it thinks about the message below.
            Colloquial Persian, first person, warm and calm, at most 8 words, in the style of «دارم به حرفت درباره‌ی ... فکر می‌کنم».
            It names the topic of the message in two or three words, so it feels related to what was written,
            but it never answers it, judges, interprets feelings or gives advice, and it never promises a question, an answer or any next step.
            No names, no quotes, no emoji.
            A greeting or a message without a topic gets exactly: «دارم پیامتو می‌خونم».
            Answer with JSON only: {"thinking": "..."}
            """;

    private final ChatRoomEventHub eventHub;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "beinema-status-line");
        thread.setDaemon(true);
        return thread;
    });

    public StatusLineWriter(ChatRoomEventHub eventHub,
                            ObjectMapper mapper,
                            @Value("${metis.base-url}") String baseUrl,
                            @Value("${metis.api-key}") String apiKey,
                            @Value("${beinema.brain.status-model:deepseek-v4-1-flash-1017}") String model) {
        this.eventHub = eventHub;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    /** In the background: write the line for these new messages and send it to the room. */
    public void startFor(Long roomId, String newMessages) {
        if (roomId == null || newMessages == null || newMessages.isBlank()) {
            return;
        }
        String text = newMessages.length() > MAX_INPUT ? newMessages.substring(newMessages.length() - MAX_INPUT) : newMessages;
        workers.submit(() -> {
            try {
                Map<String, String> lines = write(text);
                if (lines != null) {
                    eventHub.publishNamed(roomId, "agent_status", lines);
                }
            } catch (RuntimeException exception) {
                log.debug("No status lines for room {}: {}", roomId, exception.getMessage());
            }
        });
    }

    Map<String, String> write(String text) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.6,
                    "max_tokens", 160,
                    // a reasoning pass would spend the small budget and leave no answer
                    "thinking", Map.of("type", "disabled"),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", PROMPT),
                            Map.of("role", "user", "content", text))));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                            URI.create(baseUrl + "/api/v1/wrapper/deepseek/chat/completions"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                return null;
            }
            String content = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
            return parse(content);
        } catch (java.io.IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** The line, short and clean, or null. */
    Map<String, String> parse(String content) throws java.io.IOException {
        JsonNode json = mapper.readTree(content.replaceAll("(?s)^```(json)?|```$", "").trim());
        String thinking = clean(json.path("thinking").asText(""));
        return thinking.isEmpty() ? null : Map.of("thinking", thinking);
    }

    private static String clean(String line) {
        String text = line.replaceAll("[\"«»“”]", "").replaceAll("[.…]+$", "").strip();
        return text.length() > MAX_LINE ? "" : text;
    }
}
