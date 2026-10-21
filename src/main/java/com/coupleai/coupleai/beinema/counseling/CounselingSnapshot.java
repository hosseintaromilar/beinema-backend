package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The structured memory of a counseling room (stored as JSON in
 * counseling_states). Wants and readings live in client_intents; this holds the
 * rest: goal, focus per partner and its transitions, thread, known and unknown
 * facts, parked topics, each partner's engagement, constraints and unresolved
 * topics, plans, safety, governance history, the last moves and the review
 * conditions of the last decision.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CounselingSnapshot {

    public int schema = 1;
    public String goal = "";
    public String thread = "";
    public Map<String, String> focus = new LinkedHashMap<>();
    public List<Transition> transitions = new ArrayList<>();
    public List<String> known = new ArrayList<>();
    public List<String> unknown = new ArrayList<>();
    public List<String> parked = new ArrayList<>();
    public Map<String, PartnerState> partners = new LinkedHashMap<>();
    public List<Plan> plans = new ArrayList<>();
    public Safety safety = new Safety();
    public List<GovEvent> governance = new ArrayList<>();
    public List<Move> lastMoves = new ArrayList<>();
    public List<String> reviewIf = new ArrayList<>();
    public List<String> lastAnalysisKinds = new ArrayList<>();
    public int planSeq;
    /** The session manager's plan of the current session (agenda, checkpoints, how to close). */
    public SessionPlan sessionPlan;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionPlan {
        public Integer session;
        /** The session minute the plan was made at; it is made again after some time. */
        public long madeAtMinute;
        public String goal = "";
        public List<Step> steps = new ArrayList<>();
        public String closing = "";
        public List<String> watch = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Step {
        public Integer untilMinute;
        public String aim = "";
        public String stage = "NONE";
        public String who = "BOTH";
    }

    public boolean isEmpty() {
        return goal.isBlank() && thread.isBlank() && focus.isEmpty() && plans.isEmpty() && lastMoves.isEmpty()
                && known.isEmpty() && governance.isEmpty() && "NONE".equals(safety.level);
    }

    public String focusOf(String role) {
        return focus.getOrDefault(role, "NONE");
    }

    public PartnerState partner(String role) {
        return partners.computeIfAbsent(role, key -> new PartnerState());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transition {
        public String at;
        public String what;
        public String from;
        public String to;
        public String reason;
        public Long decision;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PartnerState {
        public String engagement = "";
        public List<String> constraints = new ArrayList<>();
        public List<String> unresolved = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        public String id;
        public String who;
        public String action;
        public String status;
        public String note;
        public Integer session;
        public Long decision;
        public String updatedAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Safety {
        public String level = "NONE";
        public List<String> signals = new ArrayList<>();
        public String who = "NONE";
        public String note = "";
        public String updatedAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GovEvent {
        public String gov;
        public String target;
        public Integer session;
        public Long decision;
        public String at;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Move {
        public Long decision;
        public String action;
        public String addressee;
        public String effect;
        public Boolean question;
    }
}
