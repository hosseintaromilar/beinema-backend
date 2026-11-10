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
 * The models of a companion's two minds, read and changed from the admin panel: the director (node
 * «۱ - کارگردان | تحلیل», decides each move and writes JSON) and the voice (node «۲ - صدای مشاور», writes
 * the words people read), each with thinking on or off. A change edits the companion's Metis workflow
 * draft and publishes it, only when the draft equals the live version, then reads the live version back.
 */
@Service
public class AgentModelService {

    private static final Logger log = LoggerFactory.getLogger(AgentModelService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public enum Part {
        DIRECTOR("۱ - کارگردان"), VOICE("۲ - صدای مشاور");

        final String title;

        Part(String title) {
            this.title = title;
        }
    }

    /** A model a part may run on. thinkingSwitch: whether thinking can be turned on and off for it. */
    public record Option(String provider, String model, String label, double input, double output, boolean director,
                         boolean thinkingSwitch) {
    }

    /** Only providers the companions' workflows are known to accept. Prices: Metis, dollars per million tokens. */
    public static final List<Option> OPTIONS = List.of(
            new Option("deepseek", "deepseek-v4-1-flash-1017", "DeepSeek V4.1 Flash", 0.165, 0.66, true, true),
            new Option("deepseek", "deepseek-v4-pro", "DeepSeek V4 Pro", 0.726, 2.178, true, true),
            new Option("anthropic", "claude-haiku-5-5", "Claude Haiku 5.5", 0.11, 0.55, true, true),
            new Option("anthropic", "claude-sonnet-5-5", "Claude Sonnet 5.5", 2.2, 11.0, true, true),
            new Option("grok", "grok-4.20-0309-non-reasoning", "Grok 4.20", 1.375, 2.75, false, false));

    /** What a part runs on now. thinking: null when the model decides by itself. */
    public record State(String model, String label, Boolean thinking) {
    }

    private final String key;
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public AgentModelService(@Value("${metis.api-key:}") String key, @Value("${metis.base-url:https://api.metisai.ir}") String base) {
        this.key = key;
        this.base = base.replaceAll("/+$", "");
    }

    public static Optional<Option> option(String model) {
        return OPTIONS.stream().filter(option -> option.model().equals(model)).findFirst();
    }

    /**
     * The settings that suit a part on a model. The director must return JSON: DeepSeek is told so,
     * Claude refuses that field and follows the prompt instead. The newer Claude models refuse a
     * temperature, and thinking is turned off on them by a model-specific value.
     */
    static ObjectNode args(Part part, Option option, boolean thinking) {
        ObjectNode args = JSON.createObjectNode();
        switch (option.provider()) {
            case "deepseek" -> {
                if (part == Part.DIRECTOR) {
                    args.putObject("response_format").put("type", "json_object");
                } else {
                    args.put("temperature", 0.7);
                    args.put("max_tokens", 900);
                }
                args.putObject("thinking").put("type", thinking ? "enabled" : "disabled");
            }
            case "anthropic" -> {
                args.put("max_tokens", part == Part.DIRECTOR ? 8000 : 1500);
                if (thinking) {
                    args.putObject("output_config").put("effort", part == Part.DIRECTOR ? "medium" : "low");
                } else {
                    // Sonnet 5.5 refuses "disabled": between_tools is its way of no thinking
                    args.putObject("thinking").put("type", option.model().startsWith("claude-sonnet") ? "between_tools" : "disabled");
                }
                args.putObject("cache_control").put("type", "ephemeral");
            }
            default -> {
                args.put("temperature", 0.7);
                args.put("max_tokens", 900);
            }
        }
        return args;
    }

    /** Whether thinking is on in these settings; null when nothing says so and the model decides. */
    static Boolean thinking(JsonNode config) {
        String provider = config.path("provider").path("name").asText("");
        JsonNode args = config.path("args");
        String type = args.path("thinking").path("type").asText("");
        if ("anthropic".equals(provider)) {
            return !("disabled".equals(type) || "between_tools".equals(type));
        }
        if ("deepseek".equals(provider)) {
            return type.isEmpty() ? null : "enabled".equals(type);
        }
        return null;
    }

    public Map<String, Object> read(String workflowId) {
        JsonNode workflow = workflow(get(workflowId));
        Map<String, Object> out = new LinkedHashMap<>();
        for (Part part : Part.values()) {
            try {
                JsonNode config = config(workflow, part);
                String model = config.path("provider").path("model").asText("");
                out.put(part.name().toLowerCase(), new State(model, option(model).map(Option::label).orElse(model), thinking(config)));
            } catch (IllegalArgumentException missing) {
                out.put(part.name().toLowerCase(), null);
            }
        }
        List<Map<String, Object>> options = new ArrayList<>();
        for (Option option : OPTIONS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("model", option.model());
            item.put("label", option.label());
            item.put("input", option.input());
            item.put("output", option.output());
            item.put("director", option.director());
            item.put("thinkingSwitch", option.thinkingSwitch());
            options.add(item);
        }
        out.put("options", options);
        return out;
    }

    /** Puts one part of a companion on a model, thinking on or off, and publishes its workflow. */
    public State set(String workflowId, Part part, String model, boolean thinking) {
        Option option = option(model).orElseThrow(() -> new IllegalArgumentException("این مدل در فهرست نیست."));
        if (part == Part.DIRECTOR && !option.director()) {
            throw new IllegalArgumentException("این مدل برای کارگردان مناسب نیست.");
        }
        JsonNode live = get(workflowId);
        String draftId = live.path("draftId").asText("");
        if (draftId.isBlank()) {
            throw new IllegalArgumentException("پیش‌نویس این ورک‌فلو پیدا نشد.");
        }
        ObjectNode draft = (ObjectNode) get(draftId);
        if (!draft.path("instructions").asText("").equals(live.path("instructions").asText(""))) {
            throw new IllegalArgumentException("پیش‌نویس این ورک‌فلو با نسخهٔ منتشرشده فرق دارد؛ اول آن را در متیس منتشر یا رها کن.");
        }
        ObjectNode workflow = (ObjectNode) workflow(draft);
        ObjectNode config = (ObjectNode) config(workflow, part);
        ObjectNode provider = config.putObject("provider");
        provider.put("name", option.provider());
        provider.put("model", option.model());
        config.set("args", args(part, option, thinking));
        draft.put("instructions", workflow.toString());
        send("PUT", "/api/v1/bots/" + draftId, draft.toString());
        send("POST", "/api/v1/bots/" + workflowId + "/publish", null);
        JsonNode now = config(workflow(get(workflowId)), part);
        String nowModel = now.path("provider").path("model").asText("");
        log.info("{} of workflow {} moved to {} (thinking {}); live now: {}", part, workflowId, model, thinking, nowModel);
        if (!model.equals(nowModel)) {
            throw new IllegalArgumentException("منتشر شد ولی نسخهٔ زنده هنوز مدل قبلی را نشان می‌دهد؛ چند لحظه بعد دوباره نگاه کن.");
        }
        return new State(nowModel, option.label(), thinking(now));
    }

    static JsonNode config(JsonNode workflow, Part part) {
        for (JsonNode node : workflow.path("nodes")) {
            String title = node.path("title").asText(node.path("name").asText(""));
            if (title.startsWith(part.title)) {
                JsonNode config = node.path("cards").path(0).path("config");
                if (config.isObject() && config.has("provider")) {
                    return config;
                }
            }
        }
        throw new IllegalArgumentException(part == Part.DIRECTOR ? "گره کارگردان در ورک‌فلو پیدا نشد." : "گره صدای مشاور در ورک‌فلو پیدا نشد.");
    }

    private static JsonNode workflow(JsonNode bot) {
        try {
            return JSON.readTree(bot.path("instructions").asText("{}"));
        } catch (IOException failure) {
            throw new IllegalArgumentException("ورک‌فلو خوانده نشد.");
        }
    }

    private JsonNode get(String botId) {
        try {
            String body;
            try {
                body = send("GET", "/api/v1/bots/" + botId, null);
            } catch (IllegalArgumentException firstTry) {
                // a read is safe to try once more when the network hiccups
                body = send("GET", "/api/v1/bots/" + botId, null);
            }
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
}
