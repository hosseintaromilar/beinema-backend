package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The teaching knowledge base (classpath:kb/lessons.json): short lessons of
 * choice theory the counselor can teach when the director decides the couple
 * needs a concept to move on (total behavior, basic needs, quality world, the
 * caring and deadly habits...). The director sees the catalog; the teacher lane
 * gets one whole lesson and fits it to the couple; the voice says it.
 */
public final class Lessons {

    static final String FILE = "kb/lessons.json";

    /** core and example are Persian (what is taught); when and avoid are English (for the director). */
    public record Lesson(String id, String title, String faTitle, String when, List<String> core, String example,
                         String check, List<String> avoid, List<String> related, String source) {
    }

    private static volatile Map<String, Lesson> lessons;

    private Lessons() {
    }

    public static List<Lesson> all() {
        return List.copyOf(loaded().values());
    }

    public static Optional<Lesson> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(loaded().get(id.trim().toUpperCase()));
    }

    private static Map<String, Lesson> loaded() {
        Map<String, Lesson> current = lessons;
        if (current == null) {
            synchronized (Lessons.class) {
                if (lessons == null) {
                    lessons = read();
                }
                current = lessons;
            }
        }
        return current;
    }

    static Map<String, Lesson> read() {
        JsonNode root;
        try (InputStream stream = Lessons.class.getClassLoader().getResourceAsStream(FILE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + FILE);
            }
            root = new ObjectMapper().readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable " + FILE, exception);
        }
        Map<String, Lesson> parsed = new LinkedHashMap<>();
        for (JsonNode item : root) {
            Lesson lesson = new Lesson(
                    item.path("id").asText("").trim(),
                    item.path("title").asText("").trim(),
                    item.path("fa_title").asText("").trim(),
                    item.path("when").asText("").trim(),
                    strings(item.path("core")),
                    item.path("example").asText("").trim(),
                    item.path("check").asText("").trim(),
                    strings(item.path("avoid")),
                    strings(item.path("related")),
                    item.path("source").asText("").trim()
            );
            if (!lesson.id().matches("L-[A-Z0-9-]+") || lesson.faTitle().isEmpty() || lesson.core().isEmpty()) {
                throw new IllegalStateException("Incomplete lesson in " + FILE + ": " + lesson.id());
            }
            if (parsed.put(lesson.id(), lesson) != null) {
                throw new IllegalStateException("Duplicate lesson id: " + lesson.id());
            }
        }
        return Collections.unmodifiableMap(parsed);
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    out.add(text);
                }
            });
        }
        return List.copyOf(out);
    }
}
