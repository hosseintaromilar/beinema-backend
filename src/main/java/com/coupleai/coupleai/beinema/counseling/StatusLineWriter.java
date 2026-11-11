package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.chat.ChatRoomEventHub;
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

            You also pick the small sticker the companion puts under that message at once, as a silent sign it was heard,
            or NONE. Choose by what the person did in the message, warmly and never as a verdict on them:
            LISTEN (I am listening), HERE (I am here), BESIDE (I am beside you: pain, sadness, a hard moment),
            ACK (I understand you: a feeling shared), IMPORTANT (what you say matters to me: something personal or vulnerable),
            REFLECT (I am thinking about what you said), NOTING (I am noting it: facts, details, a story),
            THANKS (thanks: they answered or explained), THANKSALOT (thank you so much: they opened up about something hard),
            SORRYUPSET (sorry you are upset), INTERESTING (interesting), GOODTHOUGHT (a good thought: a real insight of theirs),
            GOODIDEA (a good idea: their own idea or plan), NICE (how good: good news), BRAVO (well done: they did something hard),
            NOTUNDERSTOOD (I did not understand: the message is unclear).
            NONE for: any hint of danger, violence, self-harm, abuse or a crisis; anger at the companion; a message that only
            asks a question; a joke you are not sure about. Vary them: do not repeat the stickers listed as RECENT when another fits.
            Answer with JSON only: {"thinking": "...", "react": "LISTEN"}
            """;

    /** The stickers that may go under a message before the counselor has decided anything. */
    static final java.util.Set<String> EARLY = java.util.Set.of(
            "LISTEN", "HERE", "BESIDE", "ACK", "IMPORTANT", "REFLECT", "NOTING", "THANKS", "THANKSALOT",
            "SORRYUPSET", "INTERESTING", "GOODTHOUGHT", "GOODIDEA", "NICE", "BRAVO", "NOTUNDERSTOOD");

    /**
     * The early sticker of one turn and who came first: the writer puts it on only while the turn is
     * undecided; the orchestrator, deciding, learns whether it is already there (and takes it off if the
     * turn is about safety). Whoever claims first wins, so the two never overwrite each other.
     */
    public static final class Early {
        final Long messageId;
        final List<String> recent;
        private final java.util.concurrent.atomic.AtomicReference<String> state =
                new java.util.concurrent.atomic.AtomicReference<>("OPEN");

        public Early(Long messageId, List<String> recent) {
            this.messageId = messageId;
            this.recent = recent == null ? List.of() : recent;
        }

        boolean claim() {
            return state.compareAndSet("OPEN", "EARLY");
        }

        /** The turn is decided: true when the early sticker was already put on. */
        public boolean decide() {
            return !state.compareAndSet("OPEN", "DECIDED") && "EARLY".equals(state.get());
        }

        public Long messageId() {
            return messageId;
        }
    }

    /** Where the early sticker goes; optional (absent in the tests that build this by hand). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.coupleai.coupleai.beinema.chat.ChatRealtimeService realtime;

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
        startFor(roomId, newMessages, null);
    }

    /** The same, and the early sticker under the message when one fits and the turn is still undecided. */
    public void startFor(Long roomId, String newMessages, Early early) {
        if (roomId == null || newMessages == null || newMessages.isBlank()) {
            return;
        }
        String clipped = newMessages.length() > MAX_INPUT ? newMessages.substring(newMessages.length() - MAX_INPUT) : newMessages;
        String text = early == null || early.recent.isEmpty() ? clipped : clipped + "\n\nRECENT: " + String.join(", ", early.recent);
        workers.submit(() -> {
            try {
                Map<String, String> lines = write(text);
                if (lines == null) {
                    return;
                }
                if (lines.containsKey("thinking")) {
                    eventHub.publishNamed(roomId, "agent_status", Map.of("thinking", lines.get("thinking")));
                }
                String react = lines.get("react");
                if (react != null && early != null && early.messageId != null && realtime != null && early.claim()) {
                    realtime.reactIfNoneAndPublish(roomId, early.messageId, react);
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
                    "max_tokens", 200,
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
        String react = json.path("react").asText("").trim().toUpperCase(java.util.Locale.ROOT);
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (!thinking.isEmpty()) {
            out.put("thinking", thinking);
        }
        if (EARLY.contains(react)) {
            out.put("react", react);
        }
        return out.isEmpty() ? null : out;
    }

    private static String clean(String line) {
        String text = line.replaceAll("[\"«»“”]", "").replaceAll("[.…]+$", "").strip();
        return text.length() > MAX_LINE ? "" : text;
    }
}
