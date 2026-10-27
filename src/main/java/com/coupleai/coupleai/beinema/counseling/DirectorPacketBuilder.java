package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.IntentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes the turn packet the director reads (contract in
 * docs/architecture/contracts.md). Its size has a ceiling: a window of recent
 * messages, the active intents and a few knowledge cards, never the whole history.
 */
public final class DirectorPacketBuilder {

    public static final String MODE = "##BEINEMA_MODE=DIRECTOR##";
    static final int RECENT_CHARS = 700;
    static final int FRESH_CHARS = 2500;

    private DirectorPacketBuilder() {
    }

    public static String build(TurnMaterial m, String review) {
        StringBuilder out = new StringBuilder(MODE).append('\n');

        out.append("[TURN]\n")
                .append("session=").append(m.sessionNumber())
                .append(" minute=").append(m.minutesUsed()).append('/').append(m.minutesPlanned())
                .append(" new=").append(m.fresh().stream().map(message -> "#" + message.getId()).collect(Collectors.joining(",")))
                .append(" speakers=").append(m.fresh().stream().map(m::roleOf).distinct().collect(Collectors.joining(",")))
                .append('\n');

        out.append("[ROOM]\n").append("title: ").append(roomTitle(m)).append('\n');
        out.append("[PARTICIPANTS]\n");
        for (TurnMaterial.Partner partner : m.partners()) {
            out.append(partner.role()).append('=').append(partner.name()).append(" (").append(partner.status()).append(")\n");
        }

        out.append("[SAFETY_SCREEN]\n");
        if (!m.screen().any()) {
            out.append("none\n");
        } else {
            for (SafetyScreen.Cue cue : m.screen().cues()) {
                out.append(cue.severity()).append(' ').append(cue.category()).append(" \"").append(cue.phrase())
                        .append("\" in #").append(cue.messageId()).append(" by ").append(cue.role()).append('\n');
            }
        }

        out.append("[MEMORY]\n").append(memory(m));
        out.append("[MILESTONES]\n").append(milestones(m));
        out.append("[SESSION_PLAN]\n").append(SessionPlanner.forDirector(m));
        out.append("[INTENTS]\n").append(intents(m.intents()));
        out.append("[OPEN_QUESTIONS]\n").append(openQuestions(m));
        if (!m.answered().isEmpty()) {
            out.append("[ANSWER]\n").append(answers(m));
        }
        out.append("[RULES]\n");
        if (m.cards().isEmpty()) {
            out.append("none\n");
        } else {
            m.cards().forEach(card -> out.append(card.text()).append('\n'));
        }
        out.append("[LESSONS]\n").append(lessons(m));
        out.append("[RECENT_MESSAGES]\n");
        if (m.recent().isEmpty()) {
            out.append("none\n");
        }
        for (Message message : m.recent()) {
            out.append(line(m, message, RECENT_CHARS)).append('\n');
        }
        out.append("[NEW_MESSAGES]\n");
        if (m.fresh().isEmpty()) {
            out.append(m.hasMoment() ? "none; this turn is about the moment of the session below\n" : "none\n");
        }
        for (Message message : m.fresh()) {
            out.append(line(m, message, FRESH_CHARS)).append('\n');
            for (TurnInbox.Note note : m.notes()) {
                if (message.getId().equals(note.messageId())) {
                    out.append(note.text()).append('\n');
                }
            }
        }
        for (TurnInbox.Note note : m.notes()) {
            if (note.messageId() == null) {
                out.append(note.text()).append('\n');
            }
        }
        String body = neutralize(out.substring(MODE.length() + 1));
        StringBuilder packet = new StringBuilder(MODE).append('\n').append(body);
        if (review != null && !review.isBlank()) {
            packet.append("[REVIEW]\n").append(review.trim()).append('\n');
        }
        return packet.toString();
    }

    /**
     * Words the partners wrote can never switch the workflow lane: only the first
     * line of a packet carries the mode marker.
     */
    static String neutralize(String text) {
        return text == null ? "" : text.replace("##BEINEMA_MODE", "#BEINEMA-MODE");
    }

    /** The stages of their own path each partner has reached, with the session it happened in. */
    static String milestones(TurnMaterial m) {
        StringBuilder out = new StringBuilder();
        for (String role : m.partnerRoles()) {
            String reached = m.milestones().stream()
                    .filter(milestone -> role.equals(milestone.getParticipantRole()))
                    .map(milestone -> milestone.getStage() + " (session " + milestone.getSessionNumber() + ")")
                    .collect(Collectors.joining(", "));
            out.append(role).append(": ").append(reached.isBlank() ? "none yet" : reached).append('\n');
        }
        return out.length() == 0 ? "none\n" : out.toString();
    }

    /** What the counselor can teach (action TEACH), and what it already taught. */
    static String lessons(TurnMaterial m) {
        java.util.Map<String, Integer> taught = new java.util.LinkedHashMap<>();
        for (CounselingSnapshot.Taught item : m.snapshot().lessons) {
            taught.put(item.id, item.session);
        }
        StringBuilder out = new StringBuilder();
        for (Lessons.Lesson lesson : Lessons.all()) {
            out.append(lesson.id()).append(" | ").append(lesson.title());
            Integer session = taught.get(lesson.id());
            if (session != null) {
                out.append(" | TAUGHT in session ").append(session);
            }
            out.append(" | when: ").append(lesson.when()).append('\n');
        }
        out.append(teachingNote(m, taught));
        return out.toString();
    }

    /**
     * Where teaching stands in this session, so the director weighs it like a
     * real counselor would: how many lessons this session, and how far the talk
     * has gone without one.
     */
    static String teachingNote(TurnMaterial m, java.util.Map<String, Integer> taught) {
        int session = m.sessionNumber();
        long thisSession = taught.values().stream().filter(value -> value != null && value == session).count();
        Integer start = m.room() == null ? null : m.room().getSessionStartSequence();
        long partnerMessages = m.recent().stream()
                .filter(message -> message.getSenderType() == com.coupleai.coupleai.beinema.Enum.MessageSenderType.USER)
                .filter(message -> start == null || (message.getSequenceNumber() != null && message.getSequenceNumber() >= start))
                .count();
        StringBuilder note = new StringBuilder("TEACHING THIS SESSION: ")
                .append(thisSession).append(" of 2 lessons taught; ")
                .append(partnerMessages).append(" partner messages in the recent window of this session.");
        if (thisSession == 0 && partnerMessages >= 6) {
            note.append(" No lesson yet: check analysis.teach_opportunity with care; if a pattern of [LESSONS] is showing, this is a good time to teach it.");
        }
        return note.append('\n').toString();
    }

    /** The name the couple gave the room: often a hint of why they came. */
    static String roomTitle(TurnMaterial m) {
        String title = m.room() == null ? null : m.room().getTitle();
        return title == null || title.isBlank() ? "-" : "\"" + PersianText.clip(title, 120).replace('\n', ' ') + "\"";
    }

    static String memory(TurnMaterial m) {
        CounselingSnapshot s = m.snapshot();
        if (s.isEmpty()) {
            return "empty (first turn with structured memory)\n";
        }
        StringBuilder out = new StringBuilder();
        out.append("goal: ").append(blank(s.goal)).append('\n');
        out.append("thread: ").append(blank(s.thread)).append('\n');
        out.append("focus: ").append(m.partnerRoles().stream().map(role -> role + "=" + s.focusOf(role))
                .collect(Collectors.joining(" "))).append('\n');
        if (!s.known.isEmpty()) {
            out.append("known: ").append(String.join("; ", s.known)).append('\n');
        }
        if (!s.unknown.isEmpty()) {
            out.append("unknown: ").append(String.join("; ", s.unknown)).append('\n');
        }
        if (!s.parked.isEmpty()) {
            out.append("parked: ").append(String.join("; ", s.parked)).append('\n');
        }
        for (var entry : s.partners.entrySet()) {
            CounselingSnapshot.PartnerState p = entry.getValue();
            List<String> parts = new ArrayList<>();
            if (!p.engagement.isBlank()) {
                parts.add("engagement=" + p.engagement);
            }
            if (!p.constraints.isEmpty()) {
                parts.add("constraints=" + String.join("; ", p.constraints));
            }
            if (!p.unresolved.isEmpty()) {
                parts.add("unresolved=" + String.join("; ", p.unresolved));
            }
            if (!parts.isEmpty()) {
                out.append("partner ").append(entry.getKey()).append(": ").append(String.join(" | ", parts)).append('\n');
            }
        }
        if (!s.plans.isEmpty()) {
            out.append("plans: ").append(s.plans.stream()
                    .map(plan -> plan.id + " " + plan.who + " " + plan.status + " \"" + plan.action + "\" (session " + plan.session + ")"
                            + (plan.note == null || plan.note.isBlank() ? "" : " note: " + plan.note))
                    .collect(Collectors.joining("; "))).append('\n');
        }
        out.append("safety: level=").append(s.safety.level);
        if (!s.safety.signals.isEmpty()) {
            out.append(" signals=").append(String.join(", ", s.safety.signals));
        }
        if (!"NONE".equals(s.safety.who)) {
            out.append(" who=").append(s.safety.who);
        }
        out.append('\n');
        if (!s.governance.isEmpty()) {
            out.append("cautions and pauses: ").append(s.governance.stream()
                    .map(event -> event.gov + " " + event.target + " (session " + event.session + ", d" + event.decision + ")")
                    .collect(Collectors.joining("; "))).append('\n');
        }
        if (!s.lastMoves.isEmpty()) {
            out.append("last moves: ").append(s.lastMoves.stream()
                    .map(move -> "d" + move.decision + " " + move.action + "->" + move.addressee
                            + " effect=" + (move.effect == null ? "?" : move.effect))
                    .collect(Collectors.joining("; "))).append('\n');
        }
        if (!s.reviewIf.isEmpty()) {
            out.append("review_if (last decision): ").append(String.join("; ", s.reviewIf)).append('\n');
        }
        if (!s.transitions.isEmpty()) {
            CounselingSnapshot.Transition last = s.transitions.get(s.transitions.size() - 1);
            out.append("last transition: ").append(last.what).append(' ').append(last.from).append("->").append(last.to)
                    .append(" because ").append(last.reason).append('\n');
        }
        return out.toString();
    }

    static String intents(List<ClientIntent> intents) {
        List<ClientIntent> active = intents.stream()
                .filter(intent -> intent.getStatus() == IntentStatus.UNCONFIRMED || intent.getStatus() == IntentStatus.CONFIRMED)
                .toList();
        List<ClientIntent> closed = intents.stream()
                .filter(intent -> intent.getStatus() != IntentStatus.UNCONFIRMED && intent.getStatus() != IntentStatus.CONFIRMED)
                .toList();
        List<ClientIntent> shown = new ArrayList<>(active.subList(Math.max(0, active.size() - 24), active.size()));
        shown.addAll(closed.subList(Math.max(0, closed.size() - 6), closed.size()));
        if (shown.isEmpty()) {
            return "none\n";
        }
        StringBuilder out = new StringBuilder();
        for (ClientIntent intent : shown) {
            out.append('i').append(intent.getId()).append(" | ").append(intent.getParticipantRole())
                    .append(" | ").append(intent.getKind())
                    .append(" | ").append(intent.getStatus())
                    .append(" | ").append(intent.getEpistemic())
                    .append(" | ").append(intent.getConfidence() == null ? "?" : String.format(java.util.Locale.ROOT, "%.1f", intent.getConfidence()))
                    .append(" | ").append(intent.getImportance())
                    .append(" | \"").append(PersianText.clip(intent.getQuote(), 160)).append('"')
                    .append(" | ").append(PersianText.clip(intent.getInterpretation(), 200));
            if (intent.getParentId() != null) {
                out.append(" | parent i").append(intent.getParentId()).append(' ').append(intent.getChangeKind());
            }
            if (intent.getAlternativeIds() != null && !intent.getAlternativeIds().isBlank()) {
                out.append(" | alternatives ").append(intent.getAlternativeIds());
            }
            out.append('\n');
        }
        return out.toString();
    }

    static String openQuestions(TurnMaterial m) {
        if (m.openQuestions().isEmpty()) {
            return "none\n";
        }
        StringBuilder out = new StringBuilder();
        for (ClarifyingQuestion question : m.openQuestions()) {
            out.append('q').append(question.getId()).append(" -> ").append(question.getParticipantRole())
                    .append(' ').append(question.getQuestionType())
                    .append(" readings ").append(ids(question.getTargetIntentIds()))
                    .append(question.getMessageId() == null ? "" : " shown at #" + question.getMessageId())
                    .append(": \"").append(PersianText.clip(question.getQuestionText(), 200)).append("\"\n");
        }
        return out.toString();
    }

    static String answers(TurnMaterial m) {
        StringBuilder out = new StringBuilder();
        for (ClarifyingQuestion question : m.answered()) {
            QuestionOptions.Option option = QuestionOptions.find(question, question.getSelectedOptionId());
            out.append('q').append(question.getId()).append(" by ").append(question.getParticipantRole())
                    .append(": chose ").append(option == null ? "?" : option.kind())
                    .append(option != null && option.intentId() != null ? " i" + option.intentId() : "")
                    .append(option == null ? "" : " (\"" + option.label() + "\")");
            if (question.getResolutionJson() != null && !question.getResolutionJson().isBlank()) {
                out.append("; applied: ").append(question.getResolutionJson());
            }
            if (question.getFreeText() != null && !question.getFreeText().isBlank()) {
                out.append("; free text: \"").append(PersianText.clip(question.getFreeText(), 600)).append('"');
            }
            out.append('\n');
        }
        return out.toString();
    }

    static String line(TurnMaterial m, Message message, int max) {
        String role = m.roleOf(message);
        StringBuilder out = new StringBuilder("#").append(message.getId()).append(' ').append(role);
        if (!"COUNSELOR".equals(role)) {
            out.append(' ').append(m.nameOf(role));
        }
        out.append(": ");
        if (message.getReplyToMessageId() != null) {
            Message target = m.messagesById().get(message.getReplyToMessageId());
            if (target != null) {
                String who = "COUNSELOR".equals(m.roleOf(target)) ? "COUNSELOR" : m.nameOf(m.roleOf(target));
                out.append("[REPLYING TO | ").append(who).append(" #").append(target.getId()).append("] \"")
                        .append(PersianText.clip(MessageTexts.visible(target), 120).replace('\n', ' ')).append("\" ");
            }
        }
        out.append(PersianText.clip(MessageTexts.visible(message), max).replace('\n', ' '));
        List<String> files = m.fileLinesByMessageId().get(message.getId());
        if (files != null && !files.isEmpty() && !hasAttachmentNote(m, message.getId())) {
            out.append(" (+files: ").append(String.join("; ", files)).append(')');
        }
        return out.toString();
    }

    private static boolean hasAttachmentNote(TurnMaterial m, Long messageId) {
        return m.notes().stream().anyMatch(note -> messageId.equals(note.messageId()) && note.text().contains("[ATTACHMENT"));
    }

    static String lastTurns(List<DirectorDecisionRecord> turns) {
        return turns.stream().map(turn -> "d" + turn.getId() + " " + turn.getAction()).collect(Collectors.joining("; "));
    }

    private static String ids(String csv) {
        if (csv == null || csv.isBlank()) {
            return "-";
        }
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                out.add("i" + part.trim());
            }
        }
        return String.join(",", out);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
