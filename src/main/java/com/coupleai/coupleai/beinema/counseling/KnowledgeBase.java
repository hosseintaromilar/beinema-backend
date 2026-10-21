package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The professional knowledge base: principles, decision rules, safety rules,
 * contrasting pairs, counter-examples and scenario patterns, read from
 * classpath:kb/*.json. Each item becomes a short card; only the cards that fit
 * a turn are sent to the director (see KnowledgeRetriever).
 */
@Component
public class KnowledgeBase {

    private static final Map<String, KbCard.Type> FILES = Map.of(
            "kb/principles.json", KbCard.Type.PRINCIPLE,
            "kb/rules.json", KbCard.Type.RULE,
            "kb/safety.json", KbCard.Type.SAFETY,
            "kb/contrasts.json", KbCard.Type.CONTRAST,
            "kb/counterexamples.json", KbCard.Type.COUNTEREXAMPLE,
            "kb/patterns.json", KbCard.Type.PATTERN
    );

    private final List<KbCard> cards;

    public KnowledgeBase() {
        this(new ObjectMapper());
    }

    KnowledgeBase(ObjectMapper mapper) {
        List<KbCard> loaded = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, KbCard.Type> file : FILES.entrySet()) {
            for (JsonNode item : read(mapper, file.getKey())) {
                KbCard card = card(item, file.getValue());
                if (!ids.add(card.id())) {
                    throw new IllegalStateException("Duplicate knowledge base id: " + card.id());
                }
                loaded.add(card);
            }
        }
        loaded.sort((left, right) -> left.id().compareTo(right.id()));
        this.cards = Collections.unmodifiableList(loaded);
    }

    public List<KbCard> all() {
        return cards;
    }

    private static List<JsonNode> read(ObjectMapper mapper, String path) {
        try (InputStream stream = KnowledgeBase.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Knowledge base file missing: " + path);
            }
            JsonNode root = mapper.readTree(stream);
            List<JsonNode> items = new ArrayList<>();
            root.forEach(items::add);
            return items;
        } catch (IOException exception) {
            throw new IllegalStateException("Knowledge base file unreadable: " + path, exception);
        }
    }

    static KbCard card(JsonNode item, KbCard.Type type) {
        String id = text(item, "id");
        if (id.isBlank()) {
            throw new IllegalStateException("Knowledge base item without id");
        }
        Set<String> tags = new LinkedHashSet<>();
        item.path("tags").forEach(tag -> tags.add(tag.asText().trim().toUpperCase()));
        String title = text(item, "title");
        String sourceKind = item.path("source").path("kind").asText("BEINEMA_DESIGN");
        String sourceRef = item.path("source").path("ref").asText("");
        return new KbCard(id, type, title, Collections.unmodifiableSet(tags), render(item, type, id, title, sourceKind),
                sourceKind, sourceRef, text(item, "fa"));
    }

    private static String render(JsonNode item, KbCard.Type type, String id, String title, String sourceKind) {
        StringBuilder out = new StringBuilder(id);
        switch (type) {
            case RULE, SAFETY -> {
                out.append(type == KbCard.Type.SAFETY ? " [SAFETY] " : " | ").append(title);
                append(out, "when", text(item, "when"));
                append(out, "needs", text(item, "evidence"));
                append(out, "do", join(item.path("do")));
                append(out, "avoid", join(item.path("avoid")));
                append(out, "except", join(item.path("exceptions")));
            }
            case CONTRAST -> {
                out.append(" | contrast: ").append(title);
                append(out, "A", item.path("a").path("situation").asText("") + " -> " + item.path("a").path("move").asText(""));
                append(out, "B", item.path("b").path("situation").asText("") + " -> " + item.path("b").path("move").asText(""));
                append(out, "key difference", text(item, "difference"));
            }
            case COUNTEREXAMPLE -> {
                out.append(" | do NOT: ").append(text(item, "bad_move"));
                append(out, "harm", text(item, "why_harmful"));
                append(out, "instead", text(item, "instead"));
            }
            case PATTERN -> {
                out.append(" | pattern: ").append(title);
                append(out, "setup", text(item, "setup"));
                append(out, "points", join(item.path("decision_points")));
                append(out, "pitfalls", text(item, "pitfalls"));
            }
            case PRINCIPLE -> out.append(" | principle: ").append(title).append(": ").append(text(item, "text"));
        }
        out.append(" | src: ").append(sourceKind);
        return out.toString();
    }

    private static void append(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank() && !"->".equals(value.trim())) {
            out.append(" | ").append(label).append(": ").append(value.trim());
        }
    }

    private static String join(JsonNode array) {
        List<String> parts = new ArrayList<>();
        array.forEach(part -> {
            String value = part.asText("").trim();
            if (!value.isEmpty()) {
                parts.add(value);
            }
        });
        return String.join(" / ", parts);
    }

    private static String text(JsonNode item, String field) {
        return item.path(field).asText("").trim();
    }
}
