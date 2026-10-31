package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.coupleai.coupleai.beinema.Entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Set;

/**
 * The session manager: the third lane of the workflow. At the start of a session,
 * and again every so often, it writes the plan of the session (what this session
 * should reach, the steps with the minute each should be done by, and how the
 * last minutes must close it). The director reads the plan in every turn and
 * steers by it, so the counselor leads the session instead of only answering.
 */
public final class SessionPlanner {

    private static final Logger log = LoggerFactory.getLogger(SessionPlanner.class);

    public static final String MODE = "##BEINEMA_MODE=PLANNER##";

    /** A plan is made again after this many minutes of the session. */
    static final long REPLAN_MINUTES = 15;

    private static final Set<String> STAGES = Set.of("W", "D", "E", "P", "NONE");
    private static final Set<String> WHO = Set.of("OWNER", "MEMBER", "BOTH");

    private static final ObjectMapper LENIENT = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    private SessionPlanner() {
    }

    /** True when this session has no plan yet, or the plan is old. */
    static boolean due(TurnMaterial m) {
        CounselingSnapshot.SessionPlan plan = m.snapshot().sessionPlan;
        return plan == null
                || plan.session == null
                || plan.session != m.sessionNumber()
                || m.minutesUsed() >= plan.madeAtMinute + REPLAN_MINUTES;
    }

    /** Makes the plan when it is due; the old plan stays when the lane fails. */
    static void refresh(TurnMaterial m, BrainGateway brain) {
        if (!due(m)) {
            return;
        }
        try {
            BrainGateway.Reply reply = brain.forRoom(m.room()).planner(packet(m));
            CounselingSnapshot.SessionPlan plan = parse(reply.text(), m);
            if (plan != null) {
                m.snapshot().sessionPlan = plan;
            }
        } catch (RuntimeException exception) {
            log.warn("Session planner failed in room {}: {}", m.roomId(), exception.getMessage());
        }
    }

    static String packet(TurnMaterial m) {
        StringBuilder out = new StringBuilder();
        out.append("[TURN]\n")
                .append("session=").append(m.sessionNumber())
                .append(" minute=").append(m.minutesUsed()).append('/').append(m.minutesPlanned())
                .append(" (then at most 15 extra minutes for whoever goes on)\n");
        out.append("[ROOM]\ntitle: ").append(DirectorPacketBuilder.roomTitle(m)).append('\n').append(DirectorPacketBuilder.creatorNote(m));
        out.append("[PARTICIPANTS]\n");
        for (TurnMaterial.Partner partner : m.partners()) {
            out.append(partner.role()).append('=').append(partner.name()).append(" (").append(partner.status()).append(")\n");
        }
        out.append("[MILESTONES]\n").append(DirectorPacketBuilder.milestones(m));
        out.append("[MEMORY]\n").append(DirectorPacketBuilder.memory(m));
        out.append("[INTENTS]\n").append(DirectorPacketBuilder.intents(m.intents()));
        CounselingSnapshot.SessionPlan previous = m.snapshot().sessionPlan;
        if (previous != null && previous.session != null && previous.session == m.sessionNumber()) {
            out.append("[PREVIOUS_PLAN]\n").append(render(previous));
        }
        out.append("[RECENT_MESSAGES]\n");
        if (m.recent().isEmpty() && m.fresh().isEmpty()) {
            out.append("none\n");
        }
        for (Message message : m.recent()) {
            out.append(DirectorPacketBuilder.line(m, message, 400)).append('\n');
        }
        for (Message message : m.fresh()) {
            out.append(DirectorPacketBuilder.line(m, message, 800)).append('\n');
        }
        for (TurnInbox.Note note : m.notes()) {
            if (note.messageId() == null) {
                out.append(note.text()).append('\n');
            }
        }
        return MODE + "\n" + DirectorPacketBuilder.neutralize(out.toString());
    }

    static CounselingSnapshot.SessionPlan parse(String raw, TurnMaterial m) {
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
            CounselingSnapshot.SessionPlan plan = new CounselingSnapshot.SessionPlan();
            plan.session = m.sessionNumber();
            plan.madeAtMinute = m.minutesUsed();
            plan.goal = PersianText.clip(root.path("session_goal").asText(""), 300);
            for (JsonNode node : root.path("steps")) {
                if (plan.steps.size() >= 6) {
                    break;
                }
                CounselingSnapshot.Step step = new CounselingSnapshot.Step();
                step.untilMinute = node.path("until_minute").isNumber() ? node.path("until_minute").asInt() : null;
                step.aim = PersianText.clip(node.path("aim").asText(""), 240);
                String stage = node.path("stage").asText("NONE").trim().toUpperCase(Locale.ROOT);
                step.stage = STAGES.contains(stage) ? stage : "NONE";
                String who = node.path("who").asText("BOTH").trim().toUpperCase(Locale.ROOT);
                step.who = WHO.contains(who) ? who : "BOTH";
                if (!step.aim.isBlank()) {
                    plan.steps.add(step);
                }
            }
            plan.closing = PersianText.clip(root.path("closing").asText(""), 300);
            for (JsonNode node : root.path("watch")) {
                if (plan.watch.size() < 4 && !node.asText("").isBlank()) {
                    plan.watch.add(PersianText.clip(node.asText(""), 160));
                }
            }
            return plan.goal.isBlank() && plan.steps.isEmpty() ? null : plan;
        } catch (Exception exception) {
            return null;
        }
    }

    /** The plan as the director reads it, with where the session stands now. */
    static String forDirector(TurnMaterial m) {
        CounselingSnapshot.SessionPlan plan = m.snapshot().sessionPlan;
        if (plan == null || plan.session == null || plan.session != m.sessionNumber()) {
            return "none yet\n";
        }
        StringBuilder out = new StringBuilder(render(plan));
        String now = "now: minute " + m.minutesUsed() + " of " + m.minutesPlanned();
        CounselingSnapshot.Step current = plan.steps.stream()
                .filter(step -> step.untilMinute == null || step.untilMinute >= m.minutesUsed())
                .findFirst()
                .orElse(null);
        if (m.minutesUsed() >= m.minutesPlanned()) {
            now += "; the planned time is over: close the session now (see closing)";
        } else if (current != null) {
            now += "; current step: " + current.aim;
        }
        return out.append(now).append('\n').toString();
    }

    private static String render(CounselingSnapshot.SessionPlan plan) {
        StringBuilder out = new StringBuilder();
        out.append("goal: ").append(plan.goal.isBlank() ? "-" : plan.goal).append('\n');
        int index = 1;
        for (CounselingSnapshot.Step step : plan.steps) {
            out.append(index++).append(". ")
                    .append(step.untilMinute == null ? "" : "by minute " + step.untilMinute + ": ")
                    .append(step.aim)
                    .append(" [").append(step.stage).append(", ").append(step.who).append("]\n");
        }
        if (!plan.closing.isBlank()) {
            out.append("closing: ").append(plan.closing).append('\n');
        }
        if (!plan.watch.isEmpty()) {
            out.append("watch: ").append(String.join("; ", plan.watch)).append('\n');
        }
        return out.toString();
    }
}
