package com.coupleai.coupleai.beinema.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The model of each companion's counselor voice (the node «۲ - صدای مشاور» of its own Metis workflow),
 * read and changed from the admin panel. A change edits the workflow's draft and publishes it, and only
 * when the draft equals the live version, so nobody's unpublished work is overwritten.
 */
@Service
public class VoiceModelService {

    private static final Logger log = LoggerFactory.getLogger(VoiceModelService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String VOICE_NODE = "۲ - صدای مشاور";

    /** A model the voice may run on, with the settings that suit it as a short spoken reply. */
    public record Option(String provider, String model, String label, double input, double output) {

        ObjectNode args() {
            ObjectNode args = JSON.createObjectNode();
            switch (provider) {
                case "anthropic" -> {
                    // the newer Claude models refuse a temperature; little thinking for a short reply; the prompt cached
                    args.put("max_tokens", 1500);
                    args.putObject("output_config").put("effort", "low");
                    args.putObject("cache_control").put("type", "ephemeral");
                }
                case "deepseek" -> {
                    args.putObject("thinking").put("type", "disabled");
                    args.put("temperature", 0.7);
                    args.put("max_tokens", 900);
                }
                default -> {
                    args.put("temperature", 0.7);
                    args.put("max_tokens", 900);
                }
            }
            return args;
        }
    }

    /** Only providers the companions' workflows are known to accept. Prices: Metis, dollars per million tokens. */
    public static final List<Option> OPTIONS = List.of(
            new Option("anthropic", "claude-haiku-5-5", "Claude Haiku 5.5", 0.11, 0.55),
            new Option("anthropic", "claude-sonnet-5-5", "Claude Sonnet 5.5", 2.2, 11.0),
            new Option("deepseek", "deepseek-v4-1-flash-1017", "DeepSeek V4.1 Flash", 0.165, 0.66),
            new Option("deepseek", "deepseek-v4-pro", "DeepSeek V4 Pro", 0.726, 2.178),
            new Option("grok", "grok-4.20-0309-non-reasoning", "Grok 4.20", 1.375, 2.75));

    public record Voice(String slug, String name, String model, String label, String error) {
    }

    private final String key;
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public VoiceModelService(@Value("${metis.api-key:}") String key, @Value("${metis.base-url:https://api.metisai.ir}") String base) {
        this.key = key;
        this.base = base.replaceAll("/+$", "");
    }

    public static Optional<Option> option(String model) {
        return OPTIONS.stream().filter(option -> option.model().equals(model)).findFirst();
    }

    /** Every companion and the model its voice runs on now, read from its live workflow. */
    public List<Voice> list() {
        List<Voice> out = new ArrayList<>();
        for (AgentPacks.Pack pack : AgentPacks.all()) {
            try {
                JsonNode config = voiceConfig(workflow(get(pack.workflowId())));
                String model = config.path("provider").path("model").asText("");
                out.add(new Voice(pack.slug(), pack.seed().name(), model,
                        option(model).map(Option::label).orElse(model), null));
            } catch (RuntimeException failure) {
                out.add(new Voice(pack.slug(), pack.seed().name(), null, null, failure.getMessage()));
            }
        }
        return out;
    }

    /** Puts one companion's voice on another model and publishes its workflow. */
    public Voice set(String slug, String model) {
        AgentPacks.Pack pack = AgentPacks.all().stream().filter(p -> p.slug().equals(slug)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("این همراه پیدا نشد."));
        Option option = option(model).orElseThrow(() -> new IllegalArgumentException("این مدل در فهرست نیست."));
        JsonNode live = get(pack.workflowId());
        String draftId = live.path("draftId").asText("");
        if (draftId.isBlank()) {
            throw new IllegalArgumentException("پیش‌نویس این ورک‌فلو پیدا نشد.");
        }
        ObjectNode draft = (ObjectNode) get(draftId);
        if (!draft.path("instructions").asText("").equals(live.path("instructions").asText(""))) {
            throw new IllegalArgumentException("پیش‌نویس این ورک‌فلو با نسخهٔ منتشرشده فرق دارد؛ اول آن را در متیس منتشر یا رها کن.");
        }
        ObjectNode workflow = (ObjectNode) workflow(draft);
        ObjectNode config = (ObjectNode) voiceConfig(workflow);
        ObjectNode provider = config.putObject("provider");
        provider.put("name", option.provider());
        provider.put("model", option.model());
        config.set("args", option.args());
        draft.put("instructions", workflow.toString());
        send("PUT", "/api/v1/bots/" + draftId, draft.toString());
        send("POST", "/api/v1/bots/" + pack.workflowId() + "/publish", null);
        String now = voiceConfig(workflow(get(pack.workflowId()))).path("provider").path("model").asText("");
        log.info("Voice of {} moved to {} (live now: {})", slug, model, now);
        if (!model.equals(now)) {
            throw new IllegalArgumentException("منتشر شد ولی نسخهٔ زنده هنوز مدل قبلی را نشان می‌دهد؛ چند لحظه بعد دوباره نگاه کن.");
        }
        return new Voice(pack.slug(), pack.seed().name(), now, option.label(), null);
    }

    static JsonNode voiceConfig(JsonNode workflow) {
        for (JsonNode node : workflow.path("nodes")) {
            String title = node.path("title").asText(node.path("name").asText(""));
            if (title.startsWith(VOICE_NODE)) {
                JsonNode config = node.path("cards").path(0).path("config");
                if (config.isObject() && config.has("provider")) {
                    return config;
                }
            }
        }
        throw new IllegalArgumentException("گره صدای مشاور در ورک‌فلو پیدا نشد.");
    }

    private static JsonNode workflow(JsonNode bot) {
        try {
            return JSON.readTree(bot.path("instructions").asText("{}"));
        } catch (IOException failure) {
            throw new IllegalArgumentException("ورک‌فلو خوانده نشد.");
        }
    }

    private JsonNode get(String botId) {
        return readJson(send("GET", "/api/v1/bots/" + botId, null));
    }

    private static JsonNode readJson(String body) {
        try {
            return JSON.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException failure) {
            throw new IllegalArgumentException("پاسخ متیس خوانده نشد.");
        }
    }

    private String send(String method, String path, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalArgumentException("متیس درخواست را نپذیرفت (" + response.statusCode() + ").");
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("قطع شد.");
        } catch (IOException failure) {
            throw new IllegalArgumentException("اتصال به متیس ممکن نشد.");
        }
    }

    /** The options for the admin page. */
    public List<Map<String, Object>> options() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Option option : OPTIONS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("model", option.model());
            item.put("label", option.label());
            item.put("input", option.input());
            item.put("output", option.output());
            out.add(item);
        }
        return out;
    }
}
