package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lessons edited in the admin panel (with a psychologist) take the place of the file's version, by id,
 * for every library: the general one (kb/lessons.json), Elika's (kb/elika-kit.json) and Raha's beliefs
 * and practices (kb/raha-lessons.json). The edits live in the database (LessonReview); this holds them
 * for the static loaders, which ask {@link #apply} for every item they read.
 */
public final class LessonOverrides {

    public static final String GENERAL = "GENERAL";
    public static final String ELIKA = "ELIKA";
    public static final String RAHA_BELIEF = "RAHA_BELIEF";
    public static final String RAHA_PRACTICE = "RAHA_PRACTICE";

    /** One item of a library as the file has it. */
    public record Original(String library, String id, JsonNode node) {
    }

    private static final Map<String, JsonNode> EDITS = new ConcurrentHashMap<>();

    private LessonOverrides() {
    }

    static String key(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }

    /** The edited version of this item when there is one, else the item itself. */
    static JsonNode apply(JsonNode item) {
        JsonNode edited = EDITS.get(key(item.path("id").asText("")));
        return edited == null ? item : edited;
    }

    /** All edits at once (at startup), then the libraries are read again. */
    public static synchronized void replaceAll(Map<String, JsonNode> edits) {
        EDITS.clear();
        edits.forEach((id, node) -> EDITS.put(key(id), node));
        reset();
    }

    /**
     * Puts one edit (or removes it, with null) and reads every library again; when a library cannot be
     * read with it (a missing field, a broken check), the edit is taken back and the error is thrown,
     * so a bad edit never stops the lessons from working.
     */
    public static synchronized void set(String id, JsonNode edited) {
        JsonNode before = EDITS.get(key(id));
        if (edited == null) {
            EDITS.remove(key(id));
        } else {
            EDITS.put(key(id), edited);
        }
        try {
            Lessons.read();
            ElikaKit.readForCheck();
            TeachKit.read();
        } catch (RuntimeException broken) {
            if (before == null) {
                EDITS.remove(key(id));
            } else {
                EDITS.put(key(id), before);
            }
            throw new IllegalArgumentException("این تغییر درس را ناقص می‌کند: " + broken.getMessage());
        }
        reset();
    }

    private static void reset() {
        Lessons.reset();
        ElikaKit.reset();
        TeachKit.reset();
    }

    /** Every item of every library, as the files have them (without the edits). */
    public static List<Original> originals() {
        List<Original> out = new ArrayList<>();
        for (JsonNode item : file("kb/lessons.json")) {
            out.add(new Original(GENERAL, item.path("id").asText(), item));
        }
        for (JsonNode item : file("kb/elika-kit.json").path("lessons")) {
            out.add(new Original(ELIKA, item.path("id").asText(), item));
        }
        JsonNode raha = file("kb/raha-lessons.json");
        for (JsonNode item : raha.path("beliefs")) {
            out.add(new Original(RAHA_BELIEF, item.path("id").asText(), item));
        }
        for (JsonNode item : raha.path("practices")) {
            out.add(new Original(RAHA_PRACTICE, item.path("id").asText(), item));
        }
        return out;
    }

    private static JsonNode file(String name) {
        try (InputStream stream = LessonOverrides.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + name);
            }
            return new ObjectMapper().readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable " + name, exception);
        }
    }
}
