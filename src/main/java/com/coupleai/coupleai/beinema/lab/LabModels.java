package com.coupleai.coupleai.beinema.lab;

import java.util.List;
import java.util.Optional;

/**
 * The models the admin lab can call through Metis, with Metis's own prices (US dollars per million
 * tokens, from its pricing page). route says how Metis takes the call: most providers through its
 * OpenAI-style wrapper, OpenAI through its own path, Gemini through Google's own API.
 * A price here only estimates the cost shown in the lab; Metis's bill is the real one.
 */
public final class LabModels {

    public enum Route { WRAPPER, OPENAI, GEMINI }

    public record Model(String provider, String model, String label, Route route, double input, double output,
                        double cachedInput) {
    }

    public static final List<Model> ALL = List.of(
            new Model("deepseek", "deepseek-v4-1-flash-1017", "DeepSeek V4.1 Flash (فعلی)", Route.WRAPPER, 0.165, 0.66, 0.0033),
            new Model("deepseek", "deepseek-v4-pro", "DeepSeek V4 Pro", Route.WRAPPER, 0.726, 2.178, 0.0242),
            new Model("anthropic", "claude-haiku-5-5", "Claude Haiku 5.5", Route.WRAPPER, 0.11, 0.55, 0.011),
            new Model("anthropic", "claude-sonnet-5-5", "Claude Sonnet 5.5", Route.WRAPPER, 2.2, 11.0, 0.22),
            new Model("anthropic", "claude-opus-5-5", "Claude Opus 5.5", Route.WRAPPER, 4.4, 22.0, 0.22),
            new Model("grok", "grok-4.20-0309-non-reasoning", "Grok 4.20", Route.WRAPPER, 1.375, 2.75, 0.22),
            new Model("grok", "grok-4.3", "Grok 4.3", Route.WRAPPER, 1.375, 2.75, 0.22),
            new Model("openai", "gpt-6-luna", "GPT-6 Luna", Route.OPENAI, 0.11, 0.55, 0.011),
            new Model("openai", "gpt-5.6-luna", "GPT-5.6 Luna", Route.OPENAI, 0.22, 1.32, 0.022),
            new Model("openai", "gpt-4.1-mini", "GPT-4.1 mini", Route.OPENAI, 0.44, 1.76, 0.11),
            new Model("openai", "gpt-6-sol", "GPT-6 Sol", Route.OPENAI, 2.2, 11.0, 0.22),
            new Model("zai", "glm-5.3-flash", "GLM 5.3 Flash", Route.WRAPPER, 0.165, 0.55, 0.033),
            new Model("zai", "glm-5.3", "GLM 5.3", Route.WRAPPER, 1.54, 4.84, 0.286),
            new Model("google", "gemini-3.7-flash", "Gemini 3.7 Flash", Route.GEMINI, 0.825, 4.125, 0.0825),
            new Model("google", "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", Route.GEMINI, 0.33, 2.75, 0.033),
            new Model("google", "gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", Route.GEMINI, 0.11, 0.44, 0.011));

    private LabModels() {
    }

    public static Optional<Model> find(String model) {
        return ALL.stream().filter(m -> m.model().equals(model)).findFirst();
    }

    /** US dollars for these tokens; cached input is billed at its own lower price. */
    public static double cost(Model m, long input, long cachedInput, long output) {
        long fresh = Math.max(0, input - cachedInput);
        return (fresh * m.input() + cachedInput * m.cachedInput() + output * m.output()) / 1_000_000d;
    }
}
