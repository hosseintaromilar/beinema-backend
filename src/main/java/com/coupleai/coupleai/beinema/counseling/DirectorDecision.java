package com.coupleai.coupleai.beinema.counseling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The director's decision for one turn after validation (contract director.v1,
 * see docs/architecture/contracts.md). Everything the platform executes or
 * stores comes from here; notes say what the validator corrected and why.
 */
public class DirectorDecision {

    // analysis
    public String speaker = "SYSTEM";
    public List<String> kinds = new ArrayList<>();
    public String summary = "";
    public boolean toCounselor;
    public Long answersQuestion;
    public List<String> contradictions = new ArrayList<>();

    // situation
    public String goal = "";
    public String goalReason = "";
    public Map<String, String> focus = new LinkedHashMap<>();
    public String focusReason = "";
    public String thread = "";
    public List<String> knownAdd = new ArrayList<>();
    public List<String> unknownAdd = new ArrayList<>();
    public List<String> unknownResolved = new ArrayList<>();
    public List<String> parkedAdd = new ArrayList<>();
    public List<String> parkedResolved = new ArrayList<>();
    public Map<String, PartnerUpdate> partners = new LinkedHashMap<>();
    public String previousEffect = "UNKNOWN";
    public String previousNote = "";

    // memory updates
    public List<IntentOp> intents = new ArrayList<>();
    public List<PlanOp> plans = new ArrayList<>();
    /** Stages of a partner's own path reached in this turn, with a short Persian line for them. */
    public List<MilestoneOp> milestones = new ArrayList<>();

    // the move
    public String action = "LISTENING";
    public String actionLabel = "";
    public String route = "SPEAK";
    public String addressee = "BOTH";
    public String purpose = "";
    public String rationale = "";
    public List<String> rulesUsed = new ArrayList<>();
    public List<String> alternatives = new ArrayList<>();
    public String gov = "NONE";
    public String govTarget = "NONE";
    public int govDuration;
    public String react;
    public String sticker;
    public String lead = "NONE";

    /** TEACH: the lesson (kb/lessons.json) and why it helps now. */
    public String lessonId;
    public String lessonWhy = "";
    /** TEACH: what the teacher lane prepared for the voice (filled after validation). */
    public String lessonScript = "";

    public QuestionSpec question;
    public List<QuestionUpdate> questionUpdates = new ArrayList<>();

    // brief for the voice
    public String briefDo = "";
    public List<String> briefMustNot = new ArrayList<>();
    public String briefLength = "SHORT";
    public List<Long> briefReferTo = new ArrayList<>();

    public List<String> reviewIf = new ArrayList<>();

    // safety
    public String safetyLevel = "NONE";
    public List<String> safetySignals = new ArrayList<>();
    public String safetyWho = "NONE";
    public String safetyNote = "";

    /** What the validator corrected, refused or could not verify. */
    public List<String> notes = new ArrayList<>();

    public boolean silent() {
        return "SILENT".equals(route);
    }

    public static class PartnerUpdate {
        public String engagement = "";
        public List<String> constraintsAdd = new ArrayList<>();
        public List<String> unresolvedAdd = new ArrayList<>();
        public List<String> unresolvedResolved = new ArrayList<>();
    }

    /** One operation on client_intents. Existing intents are referenced by numeric id. */
    public static class IntentOp {
        public String op;
        public String ref;
        public Long id;
        public String participant;
        public String kind;
        public String quote;
        public String interpretation;
        public String epistemic;
        public double confidence = 0.5;
        public String importance = "MEDIUM";
        public List<Long> evidence = new ArrayList<>();
        /** refs (n2) or existing ids (i31), resolved after creation */
        public List<String> alternatives = new ArrayList<>();
        public Long parent;
        public String change;
        public String status;
        public String reason = "";
        /** SUPERSEDE: the replacing intent, as a ref (n1) or an existing id (i40) */
        public String by;
    }

    public static class PlanOp {
        public String op;
        public String id;
        public String participant;
        public String action;
        public String status;
        public String note = "";
    }

    public static class MilestoneOp {
        public String participant;
        /** W, D, E or P */
        public String stage;
        public String note = "";
    }

    /** A structured question as the director asked for it; readings are refs or i-ids. */
    public static class QuestionSpec {
        public String type;
        public String addressee;
        public List<String> intents = new ArrayList<>();
        public String reason = "";
        public String impact = "MEDIUM";
    }

    public static class QuestionUpdate {
        public Long id;
        public String status;
        public String reason = "";
    }
}
