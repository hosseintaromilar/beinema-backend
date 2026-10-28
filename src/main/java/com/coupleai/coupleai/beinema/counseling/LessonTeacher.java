package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.Message;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The teacher: the fourth lane of the workflow. When the director decides the
 * couple needs a concept of choice theory to move on (action TEACH with a lesson
 * of kb/lessons.json), the teacher gets that whole lesson and the couple's
 * situation and prepares what to teach and how it meets them: a bridge, two to
 * four points, an example from their own situation, a check question. The voice
 * then says it, so the couple hears the same counselor. When the lane fails the
 * lesson's own text is used.
 */
public final class LessonTeacher {

    private static final Logger log = LoggerFactory.getLogger(LessonTeacher.class);

    public static final String MODE = "##BEINEMA_MODE=TEACHER##";

    private static final int MAX_POINTS = 4;

    private static final ObjectMapper LENIENT = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    /** script: the [LESSON] block for the voice; fromTeacher: false when the lesson's own text was used. */
    public record Result(String script, boolean fromTeacher, long millis, String note) {
    }

    private LessonTeacher() {
    }

    /** Prepares d.lessonScript for a TEACH decision; nothing for other moves. */
    static Result prepare(TurnMaterial m, DirectorDecision d, BrainGateway brain) {
        if (!"TEACH".equals(d.action)) {
            return null;
        }
        Lessons.Lesson lesson = Lessons.find(d.lessonId).orElse(null);
        if (lesson == null) {
            return null;
        }
        Result result;
        try {
            BrainGateway.Reply reply = brain.forRoom(m.room()).teacher(packet(m, d, lesson));
            String script = script(reply.text(), lesson);
            result = script == null
                    ? new Result(fallback(lesson), false, reply.millis(), "teacher: unusable reply, the lesson's own text was used")
                    : new Result(script, true, reply.millis(), "teacher: lesson " + lesson.id() + " prepared");
        } catch (RuntimeException exception) {
            log.warn("Teacher failed in room {}: {}", m.roomId(), exception.getMessage());
            result = new Result(fallback(lesson), false, 0, "teacher: call failed, the lesson's own text was used");
        }
        d.lessonScript = result.script();
        if ("SHORT".equals(d.briefLength)) {
            d.briefLength = "MEDIUM";
        }
        return result;
    }

    static String packet(TurnMaterial m, DirectorDecision d, Lessons.Lesson lesson) {
        StringBuilder out = new StringBuilder();
        out.append("[ROOM]\ntitle: ").append(DirectorPacketBuilder.roomTitle(m)).append('\n');
        out.append("[PARTICIPANTS]\n");
        for (TurnMaterial.Partner partner : m.partners()) {
            out.append(partner.role()).append('=').append(partner.name()).append('\n');
        }
        out.append("[WHY]\n")
                .append("addressee=").append(d.addressee).append('\n')
                .append("why=").append(d.lessonWhy.isBlank() ? d.purpose : d.lessonWhy).append('\n')
                .append("purpose=").append(d.purpose).append('\n');
        out.append("[LESSON]\n")
                .append("id=").append(lesson.id()).append('\n')
                .append("title=").append(lesson.faTitle()).append(" (").append(lesson.title()).append(")\n");
        for (String point : lesson.core()) {
            out.append("core: ").append(point).append('\n');
        }
        out.append("example: ").append(lesson.example()).append('\n')
                .append("check: ").append(lesson.check()).append('\n');
        if (!lesson.avoid().isEmpty()) {
            out.append("avoid: ").append(String.join("; ", lesson.avoid())).append('\n');
        }
        CounselingSnapshot s = m.snapshot();
        out.append("[CONTEXT]\n");
        out.append("goal: ").append(s.goal.isBlank() ? "-" : s.goal).append('\n');
        out.append("thread: ").append(s.thread.isBlank() ? "-" : s.thread).append('\n');
        m.intents().stream()
                .filter(intent -> intent.getStatus() != null && "CONFIRMED".equals(intent.getStatus().name()))
                .limit(8)
                .forEach(intent -> out.append("confirmed of ").append(intent.getParticipantRole()).append(": ")
                        .append(intent.getInterpretation()).append('\n'));
        s.plans.stream().filter(plan -> "AGREED".equals(plan.status)).forEach(plan ->
                out.append("agreed step of ").append(plan.who).append(": ").append(plan.action).append('\n'));
        out.append("[RECENT_MESSAGES]\n");
        List<Message> all = new ArrayList<>(m.recent());
        all.addAll(m.fresh());
        List<Message> last = all.subList(Math.max(0, all.size() - 8), all.size());
        if (last.isEmpty()) {
            out.append("none\n");
        }
        for (Message message : last) {
            out.append(DirectorPacketBuilder.line(m, message, 500)).append('\n');
        }
        return MODE + "\n" + DirectorPacketBuilder.neutralize(out.toString());
    }

    /** The teacher's JSON (lesson.v1) as the [LESSON] block of the voice packet; null when unusable. */
    static String script(String raw, Lessons.Lesson lesson) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode root = LENIENT.readTree(raw.substring(start, end + 1));
            if (root == null || !root.isObject() || root.has("error")) {
                return null;
            }
            List<String> points = new ArrayList<>();
            for (JsonNode node : root.path("points")) {
                String point = node.asText("").trim();
                if (!point.isEmpty() && points.size() < MAX_POINTS) {
                    points.add(PersianText.clip(point, 400));
                }
            }
            if (points.size() < 2) {
                return null;
            }
            String check = root.path("check").asText("").trim();
            return block(lesson,
                    PersianText.clip(root.path("angle").asText(""), 300),
                    PersianText.clip(root.path("opening").asText(""), 300),
                    points,
                    PersianText.clip(root.path("example").asText(""), 500),
                    PersianText.clip(check.isEmpty() ? lesson.check() : check, 300));
        } catch (Exception exception) {
            return null;
        }
    }

    /** The lesson as written in the knowledge base, when the teacher could not prepare it. */
    static String fallback(Lessons.Lesson lesson) {
        return block(lesson, "", "", lesson.core().subList(0, Math.min(MAX_POINTS, lesson.core().size())),
                lesson.example(), lesson.check());
    }

    private static String block(Lessons.Lesson lesson, String angle, String opening, List<String> points,
                                String example, String check) {
        StringBuilder out = new StringBuilder();
        out.append("lesson=").append(lesson.faTitle()).append(" (").append(lesson.id()).append(")\n");
        if (!angle.isBlank()) {
            out.append("angle=").append(angle).append('\n');
        }
        if (!opening.isBlank()) {
            out.append("opening=").append(opening).append('\n');
        }
        for (String point : points) {
            out.append("point=").append(point).append('\n');
        }
        if (!example.isBlank()) {
            out.append("example=").append(example).append('\n');
        }
        out.append("check=").append(check).append('\n');
        return out.toString();
    }
}
