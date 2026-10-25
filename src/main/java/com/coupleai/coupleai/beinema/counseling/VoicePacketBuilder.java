package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.Message;

import java.util.List;
import java.util.Map;

/**
 * Writes what the voice reads. The voice gets the brief, the names, a short
 * window of messages and, for a structured question, the readings to turn into
 * options. It never gets the analysis, the memory or the rule cards.
 */
public final class VoicePacketBuilder {

    public static final String MODE = "##BEINEMA_MODE=VOICE##";

    /**
     * In every brief except a structured question (whose CONFIRM sentence checks
     * a reading on purpose): a reply reflects meaning, never the person's words.
     */
    static final String NO_ECHO = "do not restate or paraphrase what they wrote, and do not state guesses about them";

    private VoicePacketBuilder() {
    }

    /** readings: intent id to its English interpretation, in the order the options should follow. */
    public static String forDecision(TurnMaterial m, DirectorDecision d, Map<Long, String> readings, int window) {
        StringBuilder out = new StringBuilder(MODE).append('\n');
        participants(out, m);
        out.append("[BRIEF]\n")
                .append("action=").append(d.action).append("OTHER".equals(d.action) ? " (" + d.actionLabel + ")" : "").append('\n')
                .append("addressee=").append(addressee(m, d.addressee)).append('\n')
                .append("purpose=").append(d.purpose).append('\n')
                .append("do=").append(d.briefDo).append('\n');
        List<String> mustNot = new java.util.ArrayList<>(d.briefMustNot);
        if (!"ASK_CLARIFYING".equals(d.action)) {
            mustNot.add(NO_ECHO);
        }
        if (!mustNot.isEmpty()) {
            out.append("must_not=").append(String.join("; ", mustNot)).append('\n');
        }
        out.append("length=").append(d.briefLength).append('\n')
                .append("lead=").append(d.lead).append('\n')
                .append("governance=").append(governance(m, d)).append('\n')
                .append("safety=").append(d.safetyLevel).append('\n');
        if (!d.briefReferTo.isEmpty()) {
            out.append("about=").append(d.briefReferTo.stream().map(id -> "#" + id).reduce((a, b) -> a + "," + b).orElse(""))
                    .append('\n');
        }
        if ("TEACH".equals(d.action) && !d.lessonScript.isBlank()) {
            out.append("[LESSON]\n").append(d.lessonScript);
        }
        if (d.question != null && !readings.isEmpty()) {
            out.append("[QUESTION]\n")
                    .append("type=").append(d.question.type).append(" to ").append(addressee(m, d.question.addressee)).append('\n');
            readings.forEach((id, meaning) -> out.append('i').append(id).append(": ").append(meaning).append('\n'));
        }
        window(out, m, window);
        return MODE + "\n" + DirectorPacketBuilder.neutralize(out.substring(MODE.length() + 1));
    }

    /** A platform event (welcome, consent, session start or continue): no director, a fixed cue. */
    public static String forEvent(TurnMaterial m, String cue, int window) {
        StringBuilder out = new StringBuilder(MODE).append('\n');
        participants(out, m);
        out.append("[EVENT]\n").append(cue == null ? "" : cue.trim()).append('\n');
        CounselingSnapshot s = m.snapshot();
        if (!s.isEmpty()) {
            out.append("[CONTEXT]\n");
            if (!s.goal.isBlank()) {
                out.append("goal so far: ").append(s.goal).append('\n');
            }
            if (!s.thread.isBlank()) {
                out.append("where they were: ").append(s.thread).append('\n');
            }
            s.plans.stream().filter(plan -> "AGREED".equals(plan.status)).forEach(plan ->
                    out.append("agreed step of ").append(m.nameOf(plan.who)).append(": ").append(plan.action).append('\n'));
        }
        window(out, m, window);
        return MODE + "\n" + DirectorPacketBuilder.neutralize(out.substring(MODE.length() + 1));
    }

    private static void participants(StringBuilder out, TurnMaterial m) {
        out.append("[ROOM]\ntitle: ").append(DirectorPacketBuilder.roomTitle(m)).append('\n');
        if (m.counselorName() != null && !m.counselorName().isBlank()) {
            out.append("counselor: ").append(m.counselorName().trim()).append('\n');
        }
        out.append("[PARTICIPANTS]\n");
        for (TurnMaterial.Partner partner : m.partners()) {
            out.append(partner.role()).append('=').append(partner.name()).append('\n');
        }
    }

    private static void window(StringBuilder out, TurnMaterial m, int size) {
        List<Message> all = new java.util.ArrayList<>(m.recent());
        all.addAll(m.fresh());
        List<Message> last = all.subList(Math.max(0, all.size() - size), all.size());
        out.append("[RECENT_MESSAGES]\n");
        if (last.isEmpty()) {
            out.append("none\n");
        }
        for (Message message : last) {
            out.append(DirectorPacketBuilder.line(m, message, 600)).append('\n');
        }
        if (!m.fresh().isEmpty()) {
            out.append("[LATEST]\n").append(DirectorPacketBuilder.line(m, m.fresh().get(m.fresh().size() - 1), 1500)).append('\n');
        }
    }

    private static String addressee(TurnMaterial m, String role) {
        if (PartnerRole.isPartner(role)) {
            return role + " (" + m.nameOf(role) + ")";
        }
        return "BOTH";
    }

    private static String governance(TurnMaterial m, DirectorDecision d) {
        return switch (d.gov) {
            case "CAUTION" -> "CAUTION to " + addressee(m, d.govTarget);
            case "PAUSE_USER" -> "PAUSE of " + addressee(m, d.govTarget) + " for " + d.govDuration + " seconds";
            case "PAUSE_BOTH" -> "PAUSE of both partners for " + d.govDuration + " seconds";
            case "END_SESSION" -> "END_SESSION";
            default -> "NONE";
        };
    }
}
