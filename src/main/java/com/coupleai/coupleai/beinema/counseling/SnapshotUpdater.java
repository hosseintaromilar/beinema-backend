package com.coupleai.coupleai.beinema.counseling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies a validated decision to the structured memory. Additions are capped
 * and de-duplicated; nothing that marks safety or governance is ever dropped
 * except by the director's explicit, recorded change of level.
 */
public final class SnapshotUpdater {

    private SnapshotUpdater() {
    }

    public static void apply(CounselingSnapshot s, DirectorDecision d, Long decisionId, int session) {
        String now = Instant.now().toString();

        if (!d.goal.isBlank() && !d.goal.equals(s.goal)) {
            if (!s.goal.isBlank()) {
                transition(s, now, "goal", s.goal, d.goal, d.goalReason, decisionId);
            }
            s.goal = d.goal;
        }
        d.focus.forEach((role, letter) -> {
            String before = s.focusOf(role);
            if (!before.equals(letter)) {
                transition(s, now, "focus " + role, before, letter, d.focusReason, decisionId);
                s.focus.put(role, letter);
            }
        });
        if (!d.thread.isBlank()) {
            s.thread = d.thread;
        }
        addAll(s.known, d.knownAdd, 14);
        addAll(s.unknown, d.unknownAdd, 14);
        removeAll(s.unknown, d.unknownResolved);
        addAll(s.parked, d.parkedAdd, 10);
        removeAll(s.parked, d.parkedResolved);
        d.partners.forEach((role, update) -> {
            CounselingSnapshot.PartnerState partner = s.partner(role);
            if (!update.engagement.isBlank()) {
                partner.engagement = update.engagement;
            }
            addAll(partner.constraints, update.constraintsAdd, 8);
            addAll(partner.unresolved, update.unresolvedAdd, 8);
            removeAll(partner.unresolved, update.unresolvedResolved);
        });

        if ("TEACH".equals(d.action) && d.lessonId != null) {
            CounselingSnapshot.Taught taught = new CounselingSnapshot.Taught();
            taught.id = d.lessonId;
            taught.session = session;
            taught.at = now;
            s.lessons.add(taught);
            if (s.lessons.size() > 30) {
                s.lessons.remove(0);
            }
        }

        for (DirectorDecision.PlanOp op : d.plans) {
            if ("AGREE".equals(op.op)) {
                CounselingSnapshot.Plan plan = new CounselingSnapshot.Plan();
                plan.id = "p" + (++s.planSeq);
                plan.who = op.participant;
                plan.action = op.action;
                plan.status = "AGREED";
                plan.note = op.note;
                plan.session = session;
                plan.decision = decisionId;
                plan.updatedAt = now;
                s.plans.add(plan);
            } else if ("UPDATE".equals(op.op)) {
                s.plans.stream().filter(plan -> plan.id.equals(op.id)).findFirst().ifPresent(plan -> {
                    plan.status = op.status;
                    plan.note = op.note;
                    plan.updatedAt = now;
                });
            }
        }
        while (s.plans.size() > 20) {
            s.plans.remove(0);
        }

        if (!d.safetyLevel.equals(s.safety.level) || !d.safetySignals.isEmpty()) {
            s.safety.level = d.safetyLevel;
            addAll(s.safety.signals, d.safetySignals, 10);
            if (!"NONE".equals(d.safetyWho)) {
                s.safety.who = d.safetyWho;
            }
            if (!d.safetyNote.isBlank()) {
                s.safety.note = d.safetyNote;
            }
            s.safety.updatedAt = now;
        }

        if (!"NONE".equals(d.gov)) {
            CounselingSnapshot.GovEvent event = new CounselingSnapshot.GovEvent();
            event.gov = d.gov;
            event.target = d.govTarget;
            event.session = session;
            event.decision = decisionId;
            event.at = now;
            s.governance.add(event);
            while (s.governance.size() > 50) {
                s.governance.remove(0);
            }
        }

        if (!s.lastMoves.isEmpty()) {
            CounselingSnapshot.Move previous = s.lastMoves.get(s.lastMoves.size() - 1);
            if (previous.effect == null) {
                previous.effect = d.previousEffect;
            }
        }
        CounselingSnapshot.Move move = new CounselingSnapshot.Move();
        move.decision = decisionId;
        move.action = d.action;
        move.addressee = d.addressee;
        move.question = d.question != null;
        s.lastMoves.add(move);
        while (s.lastMoves.size() > 6) {
            s.lastMoves.remove(0);
        }
        s.reviewIf = new ArrayList<>(d.reviewIf);
        s.lastAnalysisKinds = new ArrayList<>(d.kinds);
    }

    private static void transition(CounselingSnapshot s, String now, String what, String from, String to, String reason, Long decision) {
        CounselingSnapshot.Transition transition = new CounselingSnapshot.Transition();
        transition.at = now;
        transition.what = what;
        transition.from = from;
        transition.to = to;
        transition.reason = reason == null || reason.isBlank() ? "(no reason given)" : reason;
        transition.decision = decision;
        s.transitions.add(transition);
        while (s.transitions.size() > 30) {
            s.transitions.remove(0);
        }
    }

    private static void addAll(List<String> target, List<String> values, int cap) {
        for (String value : values) {
            String key = value.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || target.stream().anyMatch(existing -> existing.trim().toLowerCase(Locale.ROOT).equals(key))) {
                continue;
            }
            target.add(value.trim());
        }
        while (target.size() > cap) {
            target.remove(0);
        }
    }

    private static void removeAll(List<String> target, List<String> values) {
        for (String value : values) {
            String key = value.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            target.removeIf(existing -> existing.trim().toLowerCase(Locale.ROOT).equals(key)
                    || (key.length() >= 8 && existing.trim().toLowerCase(Locale.ROOT).contains(key)));
        }
    }
}
