package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The teaching form of a companion (first Raha, kb/raha-lessons.json). A lesson is about the person:
 * a common belief that leads to the way they handle the problem (the belief, what it leads them to
 * do, what comes of it, why it is not accurate, a truer belief and a question to themselves). The
 * behavior recommended instead is a separate practice taken from official sources (AAP, CDC, NHS,
 * MedlinePlus, StopBullying.gov), with their names and links; the teacher never changes it.
 * A lesson is taught only after the problem was confirmed and the person's own way of handling it
 * was found not to work (DecisionValidator); it is kept as a card only that person sees.
 */
public final class TeachKit {

    /** The tag of a room whose companion teaches with cards. */
    public static final String TAG = "TEACH_CARDS";
    public static final String PREFIX = "L-RAHA-";
    static final String FILE = "kb/raha-lessons.json";

    public record Check(String question, List<String> options, int answer) {
    }

    public record Source(String name, String url) {
    }

    /** What to do instead, from official sources, word for word as we wrote it from them. */
    public record Practice(String id, String title, List<String> steps, List<Source> sources) {
    }

    public record TeachLesson(String id, String faTitle, String when, List<String> prerequisites, String belief,
                              String leadsTo, String result, List<String> whyNot, String truerBelief, String reflect,
                              Check check, Practice practice, List<String> avoid) {

        /** The same lesson in the common shape the rest of the counseling code reads. */
        public Lessons.Lesson asLesson() {
            return new Lessons.Lesson(id, faTitle, faTitle, when, whyNot, truerBelief, check.question(), avoid, List.of(),
                    String.join("; ", practice.sources().stream().map(Source::name).toList()));
        }
    }

    private static volatile Map<String, TeachLesson> lessons;

    private TeachKit() {
    }

    public static boolean owns(String id) {
        return id != null && id.trim().toUpperCase(Locale.ROOT).startsWith(PREFIX);
    }

    public static List<TeachLesson> all() {
        return List.copyOf(loaded().values());
    }

    public static Optional<TeachLesson> lesson(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(loaded().get(id.trim().toUpperCase(Locale.ROOT)));
    }

    private static Map<String, TeachLesson> loaded() {
        Map<String, TeachLesson> current = lessons;
        if (current == null) {
            synchronized (TeachKit.class) {
                if (lessons == null) {
                    lessons = read();
                }
                current = lessons;
            }
        }
        return current;
    }

    static Map<String, TeachLesson> read() {
        JsonNode root;
        try (InputStream stream = TeachKit.class.getClassLoader().getResourceAsStream(FILE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + FILE);
            }
            root = new ObjectMapper().readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable " + FILE, exception);
        }
        Map<String, Practice> practices = new LinkedHashMap<>();
        for (JsonNode item : root.path("practices")) {
            List<Source> sources = new ArrayList<>();
            item.path("sources").forEach(source -> sources.add(
                    new Source(source.path("name").asText(""), source.path("url").asText(""))));
            if (sources.isEmpty() || sources.stream().anyMatch(source -> !source.url().startsWith("https://"))) {
                throw new IllegalStateException(FILE + ": practice " + item.path("id").asText() + " needs official sources");
            }
            practices.put(item.path("id").asText(), new Practice(item.path("id").asText(), item.path("title").asText(""),
                    strings(item.path("steps")), sources));
        }
        Map<String, TeachLesson> parsed = new LinkedHashMap<>();
        for (JsonNode item : root.path("beliefs")) {
            String id = item.path("id").asText().trim().toUpperCase(Locale.ROOT);
            JsonNode check = item.path("check");
            List<String> options = strings(check.path("options"));
            int answer = check.path("answer").asInt(-1);
            if (options.size() < 2 || answer < 0 || answer >= options.size()) {
                throw new IllegalStateException(FILE + ": lesson " + id + " has no valid check");
            }
            Practice practice = practices.get(item.path("practice").asText());
            if (practice == null) {
                throw new IllegalStateException(FILE + ": lesson " + id + " has no practice");
            }
            parsed.put(id, new TeachLesson(id,
                    item.path("fa_title").asText(""),
                    item.path("when").asText(""),
                    strings(item.path("prerequisites")).stream().map(text -> text.toUpperCase(Locale.ROOT)).toList(),
                    item.path("belief").asText(""),
                    item.path("leads_to").asText(""),
                    item.path("result").asText(""),
                    strings(item.path("why_not")),
                    item.path("truer_belief").asText(""),
                    item.path("reflect").asText(""),
                    new Check(check.path("question").asText(""), options, answer),
                    practice,
                    strings(item.path("avoid"))));
        }
        return parsed;
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(node -> {
                String text = node.asText("").trim();
                if (!text.isEmpty()) {
                    out.add(text);
                }
            });
        }
        return out;
    }
}
