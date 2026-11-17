package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The lessons after a session (classpath:kb/session-lessons.json): a few for each companion, in the
 * order they are given. After every session each person gets one: fitted to them when a reading
 * about them was confirmed by them in that session, else the next of these not given to them yet.
 * A lesson may carry an exercise; a required one opens the next lesson only once it is done.
 */
public final class SessionLessons {

    static final String FILE = "kb/session-lessons.json";

    public record Exercise(boolean required, String task, String prompt) {
    }

    public record Check(String question, List<String> options, int answer) {
    }

    /** fits and avoid are English (for the writer); the rest is Persian. */
    public record Lesson(String id, AgentType companion, String faTitle, String fits, String intro, List<String> body,
                         String example, String step, Exercise exercise, Check check, List<String> avoid) {
    }

    private static volatile Map<String, Lesson> lessons;

    private SessionLessons() {
    }

    public static List<Lesson> all() {
        return List.copyOf(loaded().values());
    }

    /** The lessons of one companion, in the order they are given. */
    public static List<Lesson> of(AgentType companion) {
        return loaded().values().stream().filter(lesson -> lesson.companion() == companion).toList();
    }

    public static Optional<Lesson> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(loaded().get(id.trim().toUpperCase(Locale.ROOT)));
    }

    private static Map<String, Lesson> loaded() {
        Map<String, Lesson> current = lessons;
        if (current == null) {
            synchronized (SessionLessons.class) {
                if (lessons == null) {
                    lessons = read();
                }
                current = lessons;
            }
        }
        return current;
    }

    /** The library is read again on next use (an edit from the admin panel). */
    static void reset() {
        lessons = null;
    }

    static Map<String, Lesson> read() {
        JsonNode root;
        try (InputStream stream = SessionLessons.class.getClassLoader().getResourceAsStream(FILE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + FILE);
            }
            root = new ObjectMapper().readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable " + FILE, exception);
        }
        Map<String, Lesson> parsed = new LinkedHashMap<>();
        for (JsonNode file : root) {
            JsonNode item = LessonOverrides.apply(file);
            String id = item.path("id").asText("").trim().toUpperCase(Locale.ROOT);
            AgentType companion;
            try {
                companion = AgentType.valueOf(item.path("companion").asText(""));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalStateException(FILE + ": lesson " + id + " has no known companion");
            }
            JsonNode check = item.path("check");
            List<String> options = strings(check.path("options"));
            int answer = check.path("answer").asInt(-1);
            if (options.size() < 2 || answer < 0 || answer >= options.size() || check.path("question").asText("").isBlank()) {
                throw new IllegalStateException(FILE + ": lesson " + id + " has no valid check");
            }
            JsonNode practice = item.path("exercise");
            Exercise exercise = null;
            if (practice.isObject()) {
                String task = practice.path("task").asText("").trim();
                String prompt = practice.path("prompt").asText("").trim();
                if (task.isEmpty() || prompt.isEmpty()) {
                    throw new IllegalStateException(FILE + ": lesson " + id + " has an exercise without a task or a prompt");
                }
                exercise = new Exercise(practice.path("required").asBoolean(false), task, prompt);
            }
            Lesson lesson = new Lesson(id, companion,
                    item.path("fa_title").asText("").trim(),
                    item.path("fits").asText("").trim(),
                    item.path("intro").asText("").trim(),
                    strings(item.path("body")),
                    item.path("example").asText("").trim(),
                    item.path("step").asText("").trim(),
                    exercise,
                    new Check(check.path("question").asText("").trim(), options, answer),
                    strings(item.path("avoid")));
            if (!id.matches("S-[A-Z]+-[0-9]+") || lesson.faTitle().isEmpty() || lesson.body().size() < 2
                    || lesson.step().isEmpty()) {
                throw new IllegalStateException("Incomplete lesson in " + FILE + ": " + id);
            }
            if (parsed.put(id, lesson) != null) {
                throw new IllegalStateException("Duplicate lesson id in " + FILE + ": " + id);
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
