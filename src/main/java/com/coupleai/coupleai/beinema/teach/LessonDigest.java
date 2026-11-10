package com.coupleai.coupleai.beinema.teach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lesson cards that go out in the nightly email: the ones given that day, and once more, the
 * night after, a card whose small question is still unanswered. Each person gets only their own
 * cards, inside their own mirror email or, on a night without one, in a short email of their own.
 */
@Service
public class LessonDigest {

    /** A card older than this when the night comes is not emailed any more. */
    static final Duration FRESH = Duration.ofHours(48);
    /** A reminder waits at least this long after the first email: the next night. */
    static final Duration REMIND_AFTER = Duration.ofHours(12);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One card in the email; reminder: its question is still waiting. */
    public record Due(TeachCard card, boolean reminder) {
    }

    private final TeachCardRepository cards;

    public LessonDigest(TeachCardRepository cards) {
        this.cards = cards;
    }

    /** Everyone's due cards at this night's cutoff, by person. */
    @Transactional(readOnly = true)
    public Map<Long, List<Due>> due(Instant cutoff) {
        LocalDateTime upTo = LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault());
        Map<Long, List<Due>> byUser = new LinkedHashMap<>();
        for (TeachCard card : cards.findAllByEmailedAtIsNullAndCreatedAtGreaterThanAndCreatedAtLessThanEqual(
                upTo.minus(FRESH), upTo)) {
            byUser.computeIfAbsent(card.getUserId(), id -> new ArrayList<>()).add(new Due(card, false));
        }
        for (TeachCard card : cards.findAllByEmailedAtLessThanEqualAndRemindedAtIsNullAndChosenIsNull(cutoff.minus(REMIND_AFTER))) {
            byUser.computeIfAbsent(card.getUserId(), id -> new ArrayList<>()).add(new Due(card, true));
        }
        return byUser;
    }

    /** For the sample email: this person's newest card, as if it were given today. */
    @Transactional(readOnly = true)
    public List<Due> newestFor(Long userId) {
        return cards.findAllByUserIdOrderByIdDesc(userId).stream().limit(1).map(card -> new Due(card, false)).toList();
    }

    public List<Due> dueFor(Long userId, Instant cutoff) {
        return due(cutoff).getOrDefault(userId, List.of());
    }

    /** After the email went out: a new card is emailed, a recalled one is reminded (its own write, even inside a read). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void sent(List<Due> due, Instant at) {
        for (Due item : due) {
            TeachCard card = cards.findById(item.card().getId()).orElse(null);
            if (card == null) {
                continue;
            }
            if (item.reminder()) {
                card.setRemindedAt(at);
            } else {
                card.setEmailedAt(at);
            }
            cards.save(card);
        }
    }

    public static String subject(List<Due> due) {
        return due.stream().allMatch(Due::reminder) ? "سؤال درس‌ات هنوز منتظر جوابه" : "درس امروزت در بین‌ما";
    }

    /** The lessons as a block of the email, with inline styles like the mirror's. */
    public static String html(List<Due> due, String lessonsUrl) {
        StringBuilder out = new StringBuilder();
        out.append("<div style=\"border-top:1px dashed #d9c3a5;margin:6px 0 18px;padding-top:18px;\">")
                .append("<div style=\"font-size:16px;font-weight:bold;color:#4f5235;margin-bottom:4px;\">درس امروزت</div>")
                .append("<div style=\"font-size:12.5px;color:#8f7a66;margin-bottom:12px;\">")
                .append("این بخش فقط برای خودت است و کسی دیگر آن را نمی‌بیند.</div>");
        for (Due item : due) {
            Lesson lesson = read(item.card());
            out.append("<div style=\"background:#ffffff;border:1px solid #ecdcc5;border-top:4px solid #4f5235;border-radius:14px;")
                    .append("padding:14px 16px;margin:0 0 12px;\">");
            if (item.reminder()) {
                out.append("<div style=\"font-size:12px;color:#b86b4b;font-weight:bold;margin-bottom:4px;\">")
                        .append("هنوز به سؤال این درس جواب ندادی</div>");
            }
            out.append("<div style=\"font-size:15px;font-weight:bold;color:#2a2420;margin-bottom:8px;\">").append(esc(lesson.title())).append("</div>");
            if (notBlank(lesson.truer())) {
                out.append("<div style=\"background:#e3f0e6;border-radius:10px;padding:10px 12px;margin:0 0 10px;\">")
                        .append("<div style=\"font-size:11.5px;color:#2f6f47;font-weight:bold;\">یه نگاه درست‌تر</div>")
                        .append("<div style=\"font-size:14px;line-height:1.9;color:#1f4d31;font-weight:bold;\">").append(esc(lesson.truer())).append("</div></div>");
            }
            if (!item.reminder() && !lesson.steps().isEmpty()) {
                out.append("<div style=\"font-size:12px;color:#745c47;font-weight:bold;margin-bottom:4px;\">چه کار کنم؟ طبق منابع رسمی</div>")
                        .append("<ol style=\"margin:0 0 8px;padding:0 18px 0 0;font-size:13.5px;line-height:1.9;color:#3a2f27;\">");
                lesson.steps().forEach(step -> out.append("<li>").append(esc(step)).append("</li>"));
                out.append("</ol>");
                if (!lesson.sources().isEmpty()) {
                    out.append("<div style=\"font-size:11.5px;color:#8f7a66;\">منبع: ");
                    for (int i = 0; i < lesson.sources().size(); i++) {
                        String[] source = lesson.sources().get(i);
                        out.append(i == 0 ? "" : " · ").append("<a href=\"").append(esc(source[1]))
                                .append("\" style=\"color:#4f5235;\">").append(esc(source[0])).append("</a>");
                    }
                    out.append("</div>");
                }
            }
            if (item.card().getChosen() == null && notBlank(lesson.question())) {
                out.append("<div style=\"font-size:13.5px;color:#3a2f27;margin-top:10px;line-height:1.9;\">")
                        .append("<b>سؤال کوتاهش:</b> ").append(esc(lesson.question())).append("</div>");
            }
            out.append("</div>");
        }
        out.append("<div style=\"text-align:center;margin:4px 0 6px;\">")
                .append("<a href=\"").append(esc(lessonsUrl)).append("\" style=\"display:inline-block;background:#4f5235;")
                .append("color:#fcf2e6;text-decoration:none;font-weight:bold;font-size:14px;padding:11px 26px;border-radius:999px;\">")
                .append(due.stream().anyMatch(item -> item.card().getChosen() == null) ? "جواب دادن و دیدن درس" : "دیدن درس‌های من")
                .append("</a></div></div>");
        return out.toString();
    }

    public static String text(List<Due> due, String lessonsUrl) {
        StringBuilder out = new StringBuilder("\n\nدرس امروزت (فقط برای خودت)\n");
        for (Due item : due) {
            Lesson lesson = read(item.card());
            out.append("\n").append(item.reminder() ? "هنوز به سؤال این درس جواب ندادی: " : "").append(lesson.title()).append("\n");
            if (notBlank(lesson.truer())) {
                out.append("یه نگاه درست‌تر: ").append(lesson.truer()).append("\n");
            }
            if (!item.reminder()) {
                lesson.steps().forEach(step -> out.append("- ").append(step).append("\n"));
                lesson.sources().forEach(source -> out.append("منبع: ").append(source[0]).append(" ").append(source[1]).append("\n"));
            }
            if (item.card().getChosen() == null && notBlank(lesson.question())) {
                out.append("سؤال کوتاهش: ").append(lesson.question()).append("\n");
            }
        }
        return out.append("\n").append(lessonsUrl).append("\n").toString();
    }

    private record Lesson(String title, String truer, List<String> steps, List<String[]> sources, String question) {
    }

    private static Lesson read(TeachCard card) {
        List<String> steps = new ArrayList<>();
        List<String[]> sources = new ArrayList<>();
        String truer = "";
        String question = "";
        String title = card.getTitle();
        try {
            JsonNode content = JSON.readTree(card.getContentJson());
            title = content.path("title").asText(title);
            truer = content.path("truerBelief").asText("");
            content.path("practice").path("steps").forEach(step -> steps.add(step.asText()));
            content.path("practice").path("sources").forEach(source -> {
                String url = source.path("url").asText("");
                if (url.startsWith("https://")) {
                    sources.add(new String[]{source.path("name").asText(url), url});
                }
            });
            question = content.path("check").path("question").asText("");
        } catch (Exception unreadable) {
            // the title alone still tells them which lesson it is
        }
        return new Lesson(title, truer, steps, sources, question);
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
