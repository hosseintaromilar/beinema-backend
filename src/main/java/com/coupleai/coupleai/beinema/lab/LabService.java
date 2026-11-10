package com.coupleai.coupleai.beinema.lab;

import com.coupleai.coupleai.beinema.agents.AgentPacks;
import com.coupleai.coupleai.beinema.market.MarketDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin's model lab: one prompt to one model, straight through Metis, with the answer, the
 * time it took, the tokens and what it cost. Nothing here touches a conversation or a user.
 */
@Service
public class LabService {

    static final int MAX_PROMPT_CHARS = 80_000;
    static final int MAX_OUTPUT_TOKENS = 2_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Run(String model, String system, String user, Double temperature, Integer maxTokens) {
    }

    public record Result(String model, String label, String provider, String text, long ms, long inputTokens,
                         long cachedTokens, long outputTokens, long thinkingTokens, double costUsd, Long costToman,
                         String error) {
    }

    private final MarketDataService market;
    private final String key;
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public LabService(MarketDataService market, @Value("${metis.api-key:}") String key,
                      @Value("${metis.base-url:https://api.metisai.ir}") String base) {
        this.market = market;
        this.key = key;
        this.base = base.replaceAll("/+$", "");
    }

    public List<Map<String, Object>> models() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LabModels.Model m : LabModels.ALL) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("model", m.model());
            item.put("label", m.label());
            item.put("provider", m.provider());
            item.put("input", m.input());
            item.put("output", m.output());
            out.add(item);
        }
        return out;
    }

    /** The toman price of one dollar now, from the market prices; null when they are not read yet. */
    public Long dollar() {
        return market.snapshot().quotes().stream()
                .filter(quote -> quote.label().contains("دلار آزاد"))
                .map(quote -> Math.round(quote.price()))
                .findFirst().orElse(null);
    }

    public Result run(Run run) {
        LabModels.Model m = LabModels.find(run.model())
                .orElseThrow(() -> new IllegalArgumentException("این مدل در فهرست آزمایشگاه نیست."));
        String system = run.system() == null ? "" : run.system();
        String user = run.user() == null ? "" : run.user();
        if (user.isBlank()) {
            throw new IllegalArgumentException("متن ورودی خالی است.");
        }
        if (system.length() + user.length() > MAX_PROMPT_CHARS) {
            throw new IllegalArgumentException("پرامپت بیش از حد بلند است.");
        }
        int maxTokens = run.maxTokens() == null ? 900 : Math.max(16, Math.min(MAX_OUTPUT_TOKENS, run.maxTokens()));
        double temperature = run.temperature() == null ? 0.7 : Math.max(0, Math.min(1.5, run.temperature()));
        long started = System.currentTimeMillis();
        try {
            HttpRequest request = switch (m.route()) {
                case GEMINI -> gemini(m, system, user, temperature, maxTokens);
                case OPENAI -> chat(base + "/openai/v1/chat/completions", m, system, user, temperature, maxTokens, true);
                default -> chat(base + "/api/v1/wrapper/" + m.provider() + "/chat/completions", m, system, user, temperature, maxTokens, false);
            };
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long ms = System.currentTimeMillis() - started;
            JsonNode body = JSON.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
            if (response.statusCode() >= 300) {
                String message = body.path("message").asText(body.path("error").path("message").asText(body.toString()));
                return failed(m, ms, "HTTP " + response.statusCode() + ": " + clip(message, 300));
            }
            return m.route() == LabModels.Route.GEMINI ? fromGemini(m, body, ms) : fromChat(m, body, ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return failed(m, System.currentTimeMillis() - started, "قطع شد");
        } catch (IOException | RuntimeException failure) {
            return failed(m, System.currentTimeMillis() - started, clip(failure.getMessage(), 300));
        }
    }

    private HttpRequest chat(String url, LabModels.Model m, String system, String user, double temperature, int maxTokens,
                             boolean openai) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", m.model());
        ArrayNode messages = body.putArray("messages");
        if (!system.isBlank()) {
            messages.addObject().put("role", "system").put("content", system);
        }
        messages.addObject().put("role", "user").put("content", user);
        if (openai) {
            // the newer OpenAI models take only max_completion_tokens and their own temperature
            body.put("max_completion_tokens", maxTokens);
        } else {
            body.put("max_tokens", maxTokens);
            // the newer Claude models refuse a temperature
            if (!"anthropic".equals(m.provider())) {
                body.put("temperature", temperature);
            }
        }
        if ("deepseek".equals(m.provider())) {
            body.putObject("thinking").put("type", "disabled");
        }
        return post(url, body.toString(), "Authorization", "Bearer " + key);
    }

    private HttpRequest gemini(LabModels.Model m, String system, String user, double temperature, int maxTokens) {
        ObjectNode body = JSON.createObjectNode();
        if (!system.isBlank()) {
            body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        }
        body.putArray("contents").addObject().put("role", "user").putArray("parts").addObject().put("text", user);
        body.putObject("generationConfig").put("maxOutputTokens", maxTokens).put("temperature", temperature);
        return post(base + "/v1beta/models/" + m.model() + ":generateContent", body.toString(), "x-goog-api-key", key);
    }

    private HttpRequest post(String url, String body, String header, String value) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header(header, value)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private Result fromChat(LabModels.Model m, JsonNode body, long ms) {
        String text = body.path("choices").path(0).path("message").path("content").asText("");
        JsonNode usage = body.path("usage");
        long in = usage.path("prompt_tokens").asLong(0);
        long out = usage.path("completion_tokens").asLong(0);
        long cached = usage.path("prompt_tokens_details").path("cached_tokens").asLong(0);
        long thinking = usage.path("completion_tokens_details").path("reasoning_tokens").asLong(0);
        return result(m, text, ms, in, cached, out, thinking);
    }

    private Result fromGemini(LabModels.Model m, JsonNode body, long ms) {
        StringBuilder text = new StringBuilder();
        body.path("candidates").path(0).path("content").path("parts").forEach(part -> {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        });
        JsonNode usage = body.path("usageMetadata");
        long in = usage.path("promptTokenCount").asLong(0);
        long thinking = usage.path("thoughtsTokenCount").asLong(0);
        // thinking is billed as output
        long out = usage.path("candidatesTokenCount").asLong(0) + thinking;
        long cached = usage.path("cachedContentTokenCount").asLong(0);
        return result(m, text.toString(), ms, in, cached, out, thinking);
    }

    private Result result(LabModels.Model m, String text, long ms, long in, long cached, long out, long thinking) {
        double usd = LabModels.cost(m, in, cached, out);
        Long dollar = dollar();
        Long toman = dollar == null ? null : Math.round(usd * dollar);
        return new Result(m.model(), m.label(), m.provider(), text, ms, in, cached, out, thinking, usd, toman, null);
    }

    private Result failed(LabModels.Model m, long ms, String error) {
        return new Result(m.model(), m.label(), m.provider(), "", ms, 0, 0, 0, 0, 0, null, error);
    }

    // ------------------------------------------------------------------ what to test with

    /** The live voice prompt of a companion, read from its own Metis workflow. */
    public String voicePrompt(String slug) {
        AgentPacks.Pack pack = AgentPacks.all().stream().filter(p -> p.slug().equals(slug)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("این همراه پیدا نشد."));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/bots/" + pack.workflowId()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("خواندن ورک‌فلو از متیس ممکن نشد (" + response.statusCode() + ").");
            }
            JsonNode workflow = JSON.readTree(JSON.readTree(response.body()).path("instructions").asText("{}"));
            for (JsonNode node : workflow.path("nodes")) {
                String title = node.path("title").asText(node.path("name").asText(""));
                if (title.startsWith("۲ - صدای مشاور")) {
                    return node.path("cards").path(0).path("prompt").asText("");
                }
            }
            throw new IllegalArgumentException("گره صدای مشاور در ورک‌فلو پیدا نشد.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("قطع شد.");
        } catch (IOException failure) {
            throw new IllegalArgumentException("خواندن ورک‌فلو از متیس ممکن نشد.");
        }
    }

    /** Made-up voice packets (no real person's words) in the shape the voice gets them. */
    public List<Map<String, Object>> samples() {
        try (InputStream in = new ClassPathResource("lab/voice-samples.json").getInputStream()) {
            List<Map<String, Object>> out = new ArrayList<>();
            JSON.readTree(in).forEach(item -> out.add(Map.of(
                    "title", item.path("title").asText(),
                    "agent", item.path("agent").asText(),
                    "packet", item.path("packet").asText())));
            return out;
        } catch (IOException failure) {
            return List.of();
        }
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
