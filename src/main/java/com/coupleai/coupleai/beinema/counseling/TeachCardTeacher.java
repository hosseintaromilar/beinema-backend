package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The teacher of the teaching form. The backend hands it one of our lessons (a belief behind the way
 * the person handles the problem) with what they do now and where they stand, and it fits the
 * lesson to them as a card (teach-card.v2): what we understood, the belief, where it leads, why it is
 * not accurate, a truer belief, a question to themselves and a check. The recommended behavior is
 * not the teacher's: it is the lesson's practice from official sources, put on the card as written,
 * with its sources. When the teacher's reply is unusable the lesson's own words make the card.
 */
public final class TeachCardTeacher {

    private static final Logger log = LoggerFactory.getLogger(TeachCardTeacher.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String SCHEMA = "teach-card.v2";

    /** The teacher saw danger in the messages: no lesson now (prepare returns it as the note of an empty card). */
    public static final String SAFETY = "SAFETY";

    /** The card's content (JSON for the browser), the right option of its check, and whether it was fitted. */
    public record Fitted(String title, String contentJson, int answer, boolean fitted, long millis, String note) {
    }

    private TeachCardTeacher() {
    }

    public static Fitted prepare(TurnMaterial m, DirectorDecision d, BrainGateway brain) {
        TeachKit.TeachLesson lesson = TeachKit.lesson(d.lessonId).orElse(null);
        if (lesson == null) {
            return null;
        }
        try {
            BrainGateway.Reply reply = brain.forRoom(m.room()).teacher(packet(m, d, lesson));
            if (reply.text() != null && reply.text().contains("\"error\"") && reply.text().contains(SAFETY)) {
                return new Fitted(null, null, -1, false, reply.millis(), SAFETY);
            }
            Fitted fitted = parse(reply.text(), lesson, d, reply.millis());
            if (fitted != null) {
                return fitted;
            }
            return fallback(lesson, d, reply.millis(), "teacher: unusable card, the lesson's own words were used");
        } catch (RuntimeException exception) {
            log.warn("Teach card failed in room {}: {}", m.roomId(), exception.getMessage());
            return fallback(lesson, d, 0, "teacher: call failed, the lesson's own words were used");
        }
    }

    static String packet(TurnMaterial m, DirectorDecision d, TeachKit.TeachLesson lesson) {
        StringBuilder out = new StringBuilder();
        out.append("[ROOM]\ntitle: ").append(DirectorPacketBuilder.roomTitle(m)).append('\n');
        out.append("[FOR]\nrole=").append(d.addressee).append("\nname=").append(m.nameOf(d.addressee)).append('\n');
        out.append("[WHERE_THEY_STAND]\n")
                .append("readiness=").append(d.lessonReadiness).append('\n')
                .append("their_way=").append(d.lessonTheirWay).append('\n')
                .append("why_now=").append(d.lessonWhy.isBlank() ? d.purpose : d.lessonWhy).append('\n');
        out.append("[LESSON]\n")
                .append("id=").append(lesson.id()).append('\n')
                .append("title=").append(lesson.faTitle()).append('\n')
                .append("belief: ").append(lesson.belief()).append('\n')
                .append("leads_to: ").append(lesson.leadsTo()).append('\n')
                .append("result: ").append(lesson.result()).append('\n');
        lesson.whyNot().forEach(point -> out.append("why_not: ").append(point).append('\n'));
        out.append("truer_belief: ").append(lesson.truerBelief()).append('\n')
                .append("reflect: ").append(lesson.reflect()).append('\n')
                .append("check: ").append(lesson.check().question()).append('\n');
        for (int i = 0; i < lesson.check().options().size(); i++) {
            out.append("option ").append(i).append(i == lesson.check().answer() ? " (right)" : "").append(": ")
                    .append(lesson.check().options().get(i)).append('\n');
        }
        if (!lesson.avoid().isEmpty()) {
            out.append("avoid: ").append(String.join("; ", lesson.avoid())).append('\n');
        }
        CounselingSnapshot s = m.snapshot();
        out.append("[CONTEXT]\n")
                .append("goal: ").append(s.goal.isBlank() ? "-" : s.goal).append('\n')
                .append("thread: ").append(s.thread.isBlank() ? "-" : s.thread).append('\n');
        s.known.stream().limit(12).forEach(fact -> out.append("known: ").append(fact).append('\n'));
        out.append("[RECENT_MESSAGES]\n");
        List<Message> all = new ArrayList<>(m.recent());
        all.addAll(m.fresh());
        List<Message> last = all.subList(Math.max(0, all.size() - 10), all.size());
        for (Message message : last) {
            out.append(DirectorPacketBuilder.line(m, message, 500)).append('\n');
        }
        return LessonTeacher.MODE + "\n" + DirectorPacketBuilder.neutralize(out.toString());
    }

    /** The teacher's card when it is whole and keeps a valid check; null otherwise. */
    static Fitted parse(String raw, TeachKit.TeachLesson lesson, DirectorDecision d, long millis) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            JsonNode card = JSON.readTree(raw.substring(start, end + 1));
            if (!SCHEMA.equals(card.path("schema").asText())) {
                return null;
            }
            List<String> whyNot = texts(card.path("why_not"), 4, 400);
            List<String> options = texts(card.path("check").path("options"), 4, 160);
            int answer = card.path("check").path("answer").asInt(-1);
            String question = text(card.path("check").path("question"), 240);
            String situation = text(card.path("situation"), 600);
            String belief = text(card.path("belief"), 300);
            String truer = text(card.path("truer_belief"), 300);
            if (whyNot.size() < 2 || options.size() < 2 || answer < 0 || answer >= options.size()
                    || question.isBlank() || situation.isBlank() || belief.isBlank() || truer.isBlank()) {
                return null;
            }
            String title = text(card.path("title"), 120);
            ObjectNode content = content(title.isBlank() ? lesson.faTitle() : title, situation, belief,
                    text(card.path("leads_to"), 300), text(card.path("result"), 300), whyNot, truer,
                    text(card.path("reflect"), 240), question, options, lesson.practice(), d.lessonReadiness);
            return new Fitted(title.isBlank() ? lesson.faTitle() : title, JSON.writeValueAsString(content), answer, true,
                    millis, "teacher: card " + lesson.id() + " fitted");
        } catch (Exception broken) {
            return null;
        }
    }

    /** The lesson's own words, with what they do now as what we understood. */
    static Fitted fallback(TeachKit.TeachLesson lesson, DirectorDecision d, long millis, String note) {
        ObjectNode content = content(lesson.faTitle(), d.lessonTheirWay, lesson.belief(), lesson.leadsTo(), lesson.result(),
                lesson.whyNot(), lesson.truerBelief(), lesson.reflect(), lesson.check().question(), lesson.check().options(),
                lesson.practice(), d.lessonReadiness);
        try {
            return new Fitted(lesson.faTitle(), JSON.writeValueAsString(content), lesson.check().answer(), false, millis, note);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ObjectNode content(String title, String situation, String belief, String leadsTo, String result,
                                      List<String> whyNot, String truerBelief, String reflect, String question,
                                      List<String> options, TeachKit.Practice practice, String readiness) {
        ObjectNode node = JSON.createObjectNode();
        node.put("title", title);
        node.put("situation", situation == null ? "" : situation);
        node.put("belief", belief);
        node.put("leadsTo", leadsTo == null ? "" : leadsTo);
        node.put("result", result == null ? "" : result);
        ArrayNode reasons = node.putArray("whyNot");
        whyNot.forEach(reasons::add);
        node.put("truerBelief", truerBelief);
        node.put("reflect", reflect == null ? "" : reflect);
        ObjectNode check = node.putObject("check");
        check.put("question", question);
        ArrayNode choices = check.putArray("options");
        options.forEach(choices::add);
        // the recommended behavior is ours from official sources: never the teacher's words
        ObjectNode doing = node.putObject("practice");
        doing.put("title", practice.title());
        ArrayNode steps = doing.putArray("steps");
        practice.steps().forEach(steps::add);
        ArrayNode sources = doing.putArray("sources");
        practice.sources().forEach(source -> {
            ObjectNode item = sources.addObject();
            item.put("name", source.name());
            item.put("url", source.url());
        });
        node.put("readiness", readiness);
        return node;
    }

    private static List<String> texts(JsonNode array, int max, int chars) {
        List<String> out = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode item : array) {
                String value = text(item, chars);
                if (!value.isBlank() && out.size() < max) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    private static String text(JsonNode node, int max) {
        String value = node == null || node.isNull() ? "" : node.asText("").trim();
        return value.length() > max ? value.substring(0, max) : value;
    }
}
