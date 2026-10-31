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
 * Elika's own material (classpath:kb/elika-kit.json): the tests a couple answers
 * separately and the lessons she teaches with a visual. Only rooms on a workflow of
 * beinema.brain.kit-workflow-ids see any of it. The comparison of two people's answers
 * is computed here, not by the model, so the same answers always give the same result.
 */
public final class ElikaKit {

    static final String FILE = "kb/elika-kit.json";
    public static final String LESSON_PREFIX = "EL-";
    /** The tag of a room whose companion works with this kit. */
    public static final String TAG = "KIT";

    /** The second step of every SCALE item: how firm this answer is. */
    public static final List<String> FIRMNESS = List.of("خط قرمزمه", "ترجیحمه، ولی می‌شه حرف زد", "برام فرقی نداره");
    public static final String FIRMNESS_PROMPT = "این یکی برات چقدر قطعیه؟";
    public static final String FLEX_SUFFIX = ".flex";

    public static final String ALIGNED = "ALIGNED";
    public static final String WORKABLE = "WORKABLE";
    public static final String CORE_GAP = "CORE_GAP";

    public record Item(String id, String label, String prompt, List<String> options, boolean reverse, boolean hidden) {
    }

    public record Test(String key, String kind, String title, String intro, int minutes, String when, List<Item> items) {
        public boolean scale() {
            return "SCALE".equals(kind);
        }

        public Optional<Item> item(String id) {
            return items.stream().filter(item -> item.id().equals(id)).findFirst();
        }
    }

    /** One row of a comparison: the two raw answers, firmness when asked, and what it means. */
    public record Row(String id, String label, List<String> options, int first, int second,
                      Integer firstFirmness, Integer secondFirmness, String status, boolean seenDifferently) {
    }

    public record Comparison(List<Row> rows, String level, String title, String text) {
    }

    private record Loaded(Map<String, Test> tests, Map<String, Lessons.Lesson> lessons, Map<String, Map<String, Object>> cards) {
    }

    private static volatile Loaded loaded;

    private ElikaKit() {
    }

    public static List<Test> tests() {
        return List.copyOf(load().tests().values());
    }

    public static Optional<Test> test(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(load().tests().get(key.trim().toUpperCase()));
    }

    public static List<Lessons.Lesson> lessons() {
        return List.copyOf(load().lessons().values());
    }

    public static Optional<Lessons.Lesson> lesson(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(load().lessons().get(id.trim().toUpperCase()));
    }

    /** What the lesson card of the chat shows. */
    public static Optional<Map<String, Object>> lessonCard(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(load().cards().get(id.trim().toUpperCase()));
    }

    /** All the answers are there and within the options. */
    public static boolean complete(Test test, Map<String, Integer> answers) {
        if (answers == null) {
            return false;
        }
        for (Item item : test.items()) {
            if (!inRange(answers.get(item.id()), item.options().size())) {
                return false;
            }
            if (test.scale() && !inRange(answers.get(item.id() + FLEX_SUFFIX), FIRMNESS.size())) {
                return false;
            }
        }
        return true;
    }

    private static boolean inRange(Integer value, int size) {
        return value != null && value >= 0 && value < size;
    }

    public static Comparison compare(Test test, Map<String, Integer> first, Map<String, Integer> second) {
        return test.scale() ? compareScale(test, first, second) : compareLikert(test, first, second);
    }

    /** How far apart two answers are, and whether either one is a red line. */
    static String scaleStatus(int a, int firmA, int b, int firmB) {
        int gap = Math.abs(a - b);
        if (gap == 0 || firmA == 2 || firmB == 2) {
            return ALIGNED;
        }
        boolean bothFirm = firmA == 0 && firmB == 0;
        if (gap == 1) {
            return bothFirm ? WORKABLE : ALIGNED;
        }
        return bothFirm ? CORE_GAP : WORKABLE;
    }

    private static Comparison compareScale(Test test, Map<String, Integer> first, Map<String, Integer> second) {
        List<Row> rows = new ArrayList<>();
        int gaps = 0;
        int workable = 0;
        for (Item item : test.items()) {
            int a = first.get(item.id());
            int b = second.get(item.id());
            int firmA = first.get(item.id() + FLEX_SUFFIX);
            int firmB = second.get(item.id() + FLEX_SUFFIX);
            String status = scaleStatus(a, firmA, b, firmB);
            gaps += CORE_GAP.equals(status) ? 1 : 0;
            workable += WORKABLE.equals(status) ? 1 : 0;
            rows.add(new Row(item.id(), item.label(), item.options(), a, b, firmA, firmB, status, false));
        }
        if (gaps > 0) {
            return new Comparison(rows, "SPLIT", "توی " + java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("fa-IR")).format(gaps) + " موضوع هنوز هم‌مسیر نیستید",
                    "این‌جاها جواب‌هاتون از هم دوره و برای هر دو قطعیه. تا روشن نشه، بقیهٔ توافق‌ها روش نمی‌ایسته.");
        }
        if (workable >= 2) {
            return new Comparison(rows, "TALK", "مسیرتون نزدیکه، ولی چند گفتگوی جدی لازمه",
                    "خط قرمز روبه‌روی هم ندارید، ولی چند جا فاصله هست که باید قبل از تصمیم درباره‌ش توافق کنید.");
        }
        return new Comparison(rows, "SAME", "فعلاً هم‌مسیرید",
                "توی این موضوع‌ها جواب‌هاتون نزدیکه یا راحت کنار میاد. این فقط چیزیه که خودتون گفتید؛ رفتار واقعی رو هم باید دید.");
    }

    /** How worrying one answer is, from 0 to 3, whichever way the statement is worded. */
    static int risk(Item item, int answer) {
        return item.reverse() ? 3 - answer : answer;
    }

    private static Comparison compareLikert(Test test, Map<String, Integer> first, Map<String, Integer> second) {
        List<Row> rows = new ArrayList<>();
        int serious = 0;
        int watch = 0;
        for (Item item : test.items()) {
            if (item.hidden()) {
                continue;
            }
            int a = first.get(item.id());
            int b = second.get(item.id());
            int worst = Math.max(risk(item, a), risk(item, b));
            boolean grave = worst == 3 || ("CONTEMPT".equals(item.id()) && worst >= 2);
            String status = grave ? CORE_GAP : worst == 2 ? WORKABLE : ALIGNED;
            serious += grave ? 1 : 0;
            watch += worst == 2 && !grave ? 1 : 0;
            rows.add(new Row(item.id(), item.label(), item.options(), a, b, null, null, status, Math.abs(a - b) >= 2));
        }
        if (serious > 0) {
            return new Comparison(rows, "SPLIT", "روش اختلافتون قبل از هر تصمیمی کار لازم داره",
                    "این‌جاها نشونه‌هایی هست که اگه همین‌طور بمونه، بعداً سنگین‌تر می‌شه. مهم اینه که هر دو ببینیدش و بخواید تمرین کنید.");
        }
        if (watch >= 2) {
            return new Comparison(rows, "TALK", "پایه خوبه، ولی چند عادت باید بهتر بشه",
                    "چیز خطرناکی پررنگ نیست، ولی چند الگو هست که با تمرین می‌شه خیلی بهترش کرد.");
        }
        return new Comparison(rows, "SAME", "روش حرف زدنتون سالمه",
                "از جواب‌های شما، اختلاف‌هاتون بدون زخم جدی جلو می‌ره. همین یکی از قوی‌ترین سرمایه‌های یه رابطه‌ست.");
    }

    private static Loaded load() {
        Loaded current = loaded;
        if (current == null) {
            synchronized (ElikaKit.class) {
                if (loaded == null) {
                    loaded = read();
                }
                current = loaded;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Loaded read() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try (InputStream stream = ElikaKit.class.getClassLoader().getResourceAsStream(FILE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + FILE);
            }
            root = mapper.readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable " + FILE, exception);
        }
        Map<String, Test> tests = new LinkedHashMap<>();
        for (JsonNode node : root.path("tests")) {
            List<String> shared = strings(node.path("options"));
            List<Item> items = new ArrayList<>();
            for (JsonNode item : node.path("items")) {
                List<String> own = strings(item.path("options"));
                items.add(new Item(item.path("id").asText(), item.path("label").asText(), item.path("prompt").asText(),
                        own.isEmpty() ? shared : own, item.path("reverse").asBoolean(false), item.path("private").asBoolean(false)));
            }
            Test test = new Test(node.path("key").asText(), node.path("kind").asText(), node.path("title").asText(),
                    node.path("intro").asText(), node.path("minutes").asInt(3), node.path("when").asText(), List.copyOf(items));
            if (test.items().isEmpty() || test.items().stream().anyMatch(item -> item.options().size() < 2)) {
                throw new IllegalStateException("Incomplete test in " + FILE + ": " + test.key());
            }
            tests.put(test.key(), test);
        }
        Map<String, Lessons.Lesson> lessons = new LinkedHashMap<>();
        Map<String, Map<String, Object>> cards = new LinkedHashMap<>();
        for (JsonNode node : root.path("lessons")) {
            String id = node.path("id").asText();
            List<String> core = strings(node.path("core"));
            if (!id.startsWith(LESSON_PREFIX) || core.isEmpty() || node.path("visual").isMissingNode()) {
                throw new IllegalStateException("Incomplete lesson in " + FILE + ": " + id);
            }
            lessons.put(id, new Lessons.Lesson(id, node.path("title").asText(), node.path("fa_title").asText(),
                    node.path("when").asText(), core, node.path("example").asText(), node.path("check").asText(),
                    strings(node.path("avoid")), List.of(), node.path("source").asText()));
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", id);
            card.put("title", node.path("fa_title").asText());
            card.put("points", core);
            card.put("example", node.path("example").asText());
            card.put("visual", mapper.convertValue(node.path("visual"), Map.class));
            card.put("try", node.path("try").asText());
            card.put("source", node.path("source").asText());
            cards.put(id, card);
        }
        return new Loaded(Collections.unmodifiableMap(tests), Collections.unmodifiableMap(lessons), Collections.unmodifiableMap(cards));
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.forEach(item -> out.add(item.asText("").trim()));
        return List.copyOf(out);
    }
}
