package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the director's JSON into a DirectorDecision the platform can execute.
 * It never chooses a counseling move. It only refuses or corrects what cannot be
 * executed or would break a rule the platform guarantees:
 * <ul>
 *   <li>unknown values, ids, partners and messages;</li>
 *   <li>a STATED quote that is not in that partner's messages becomes INFERRED;</li>
 *   <li>a confirmation without evidence from that partner is dropped;</li>
 *   <li>structured questions outside their limits become an open question;</li>
 *   <li>at safety level CONCERN or URGENT: no structured question, no routine
 *       work, no caution or pause for the person at risk.</li>
 * </ul>
 * Every correction is written to notes, so the trace shows it.
 */
public final class DecisionValidator {

    public static final Set<String> ACTIONS = Set.of(
            "RESPOND", "REFLECT", "SUMMARIZE", "ASK_OPEN", "ASK_CLARIFYING", "LISTENING", "WAIT",
            "EXPLORE_DOING", "FACILITATE_EVALUATION", "SUPPORT_PLANNING", "FOLLOW_UP_PLAN", "REVISIT_WANT",
            "RETURN_TO_THREAD", "INTERVENE", "INVITE_QUIET", "SAFETY_ASSESS", "SAFETY_RESPONSE",
            "LIMIT_ROUTINE", "END_SESSION", "TEACH", "OTHER"
    );
    /** Ordinary work that stops while a safety concern is open. */
    static final Set<String> ROUTINE = Set.of(
            "ASK_CLARIFYING", "EXPLORE_DOING", "FACILITATE_EVALUATION", "SUPPORT_PLANNING", "FOLLOW_UP_PLAN",
            "REVISIT_WANT", "RETURN_TO_THREAD", "WAIT", "SUMMARIZE", "TEACH"
    );
    private static final Set<String> KINDS = Set.of(
            "WANT", "BEHAVIOR", "FEELING", "PERCEPTION_OF_PARTNER", "ANSWER_TO_QUESTION", "NEW_INFO", "RESISTANCE",
            "CONTRADICTION", "WANT_CHANGE", "SAFETY_SIGNAL", "QUESTION_TO_COUNSELOR", "GREETING", "OTHER"
    );
    private static final Set<String> SPEAKERS = Set.of("OWNER", "MEMBER", "BOTH", "SYSTEM");
    private static final Set<String> ADDRESSEES = Set.of("OWNER", "MEMBER", "BOTH");
    private static final Set<String> FOCUS = Set.of("W", "D", "E", "P", "NONE");
    private static final Set<String> EFFECTS = Set.of("HELPFUL", "NEUTRAL", "UNHELPFUL", "UNKNOWN");
    private static final Set<String> INTENT_KINDS = Set.of("WANT", "MEANING", "CONCERN", "GOAL");
    private static final Set<String> EPISTEMIC = Set.of("STATED", "INFERRED", "HYPOTHESIS");
    private static final Set<String> IMPORTANCE = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> GOV = Set.of("NONE", "CAUTION", "PAUSE_USER", "PAUSE_BOTH", "END_SESSION");
    private static final Set<String> TARGETS = Set.of("OWNER", "MEMBER", "BOTH", "NONE");
    private static final Set<String> LEADS = Set.of("NONE", "BRIDGE", "PARK", "INVITE_QUIET", "CLOSE_LOOP", "TRANSITION");
    private static final Set<String> SAFETY = Set.of("NONE", "WATCH", "CONCERN", "URGENT");
    private static final Set<String> PLAN_STATUS = Set.of("DONE", "NOT_DONE", "REVISED", "DROPPED");
    private static final Pattern ID = Pattern.compile("^[#a-zA-Z]?\\s*(\\d{1,12})$");
    private static final Pattern RULE_ID = Pattern.compile("^[A-Z]{1,3}(-[A-Z0-9]{1,6}){1,3}$");

    /** What the validator needs to know about the room. */
    public record Context(
            Set<String> partnersPresent,
            String defaultAddressee,
            Map<String, List<String>> looseTextsByRole,
            Map<Long, String> roleByMessageId,
            Map<Long, IntentView> intents,
            Map<Long, QuestionView> openQuestions,
            Set<String> planIds,
            int questionsThisSession,
            int maxQuestionsPerSession,
            boolean screenHigh,
            String currentSafetyLevel,
            Map<String, Set<String>> reachedStages
    ) {
    }

    static final List<String> STAGES = List.of("W", "D", "E", "P");

    public record IntentView(Long id, String role, String status) {
    }

    public record QuestionView(Long id, String role) {
    }

    private DecisionValidator() {
    }

    public static DirectorDecision validate(JsonNode root, Context ctx) {
        DirectorDecision d = new DirectorDecision();
        readAnalysis(root.path("analysis"), d, ctx);
        readSituation(root.path("situation"), d, ctx);
        readIntents(root.path("intents"), d, ctx);
        readPlans(root.path("plans"), d, ctx);
        readMilestones(root.path("milestones"), d, ctx);
        readDecision(root.path("decision"), d, ctx);
        readLesson(root.path("lesson"), d);
        readQuestionUpdates(root.path("question_updates"), d, ctx);
        readBrief(root.path("brief"), d, ctx);
        d.reviewIf = strings(root.path("review_if"), 5, 240);
        readSafety(root.path("safety"), d, ctx);
        readQuestion(root.path("question"), d, ctx);
        enforceSafety(d, ctx);
        enforceConsistency(d, ctx);
        return d;
    }

    /** The safe default when the director could not decide: brief presence, nothing stored. */
    public static DirectorDecision fallback(String addressee, String reason) {
        DirectorDecision d = new DirectorDecision();
        d.action = "LISTENING";
        d.route = "SPEAK";
        d.addressee = addressee == null ? "BOTH" : addressee;
        d.purpose = "stay present while the decision could not be made";
        d.rationale = "fallback: " + reason;
        d.briefDo = "Stay present with one very short, warm sentence. No question, no analysis.";
        d.briefLength = "SHORT";
        d.notes.add("fallback: " + reason);
        return d;
    }

    // ------------------------------------------------------------------ parts

    private static void readAnalysis(JsonNode a, DirectorDecision d, Context ctx) {
        d.speaker = oneOf(a.path("speaker").asText(""), SPEAKERS, ctx.defaultAddressee());
        d.kinds = upperList(a.path("kinds"), KINDS, 10);
        d.summary = clip(a.path("summary").asText(""), 400);
        d.toCounselor = a.path("to_counselor").asBoolean(false);
        Long answers = id(a.path("answers_question").asText(""));
        d.answersQuestion = answers;
        d.contradictions = strings(a.path("contradictions"), 5, 300);
    }

    private static void readSituation(JsonNode s, DirectorDecision d, Context ctx) {
        d.goal = clip(s.path("goal").asText(""), 300);
        d.goalReason = clip(s.path("goal_reason").asText(""), 300);
        for (String role : List.of(PartnerRole.OWNER, PartnerRole.MEMBER)) {
            String letter = s.path("focus").path(role).asText("").trim().toUpperCase(Locale.ROOT);
            if (FOCUS.contains(letter) && ctx.partnersPresent().contains(role)) {
                d.focus.put(role, letter);
            }
        }
        d.focusReason = clip(s.path("focus_reason").asText(""), 300);
        d.thread = clip(s.path("thread").asText(""), 300);
        d.knownAdd = strings(s.path("known_add"), 5, 200);
        d.unknownAdd = strings(s.path("unknown_add"), 5, 200);
        d.unknownResolved = strings(s.path("unknown_resolved"), 5, 200);
        d.parkedAdd = strings(s.path("parked_add"), 3, 200);
        d.parkedResolved = strings(s.path("parked_resolved"), 3, 200);
        for (String role : ctx.partnersPresent()) {
            JsonNode p = s.path("participants").path(role);
            if (p.isMissingNode() || p.isNull()) {
                continue;
            }
            DirectorDecision.PartnerUpdate update = new DirectorDecision.PartnerUpdate();
            update.engagement = clip(p.path("engagement").asText(""), 200);
            update.constraintsAdd = strings(p.path("constraints_add"), 3, 200);
            update.unresolvedAdd = strings(p.path("unresolved_add"), 3, 200);
            update.unresolvedResolved = strings(p.path("unresolved_resolved"), 3, 200);
            d.partners.put(role, update);
        }
        JsonNode outcome = s.path("previous_outcome");
        d.previousEffect = oneOf(outcome.path("effect").asText(""), EFFECTS, "UNKNOWN");
        d.previousNote = clip(outcome.path("note").asText(""), 300);
    }

    private static void readIntents(JsonNode array, DirectorDecision d, Context ctx) {
        if (!array.isArray()) {
            return;
        }
        Set<String> refs = new HashSet<>();
        int count = 0;
        for (JsonNode node : array) {
            if (count >= 10) {
                d.notes.add("intents: more than 10 operations, the rest ignored");
                break;
            }
            String op = node.path("op").asText("").trim().toUpperCase(Locale.ROOT);
            DirectorDecision.IntentOp item = switch (op) {
                case "CREATE" -> create(node, d, ctx, refs);
                case "STATUS" -> status(node, d, ctx);
                case "SUPERSEDE" -> supersede(node, d, ctx);
                case "PRIORITY" -> priority(node, d, ctx);
                default -> {
                    d.notes.add("intents: unknown op '" + op + "' ignored");
                    yield null;
                }
            };
            if (item != null) {
                d.intents.add(item);
                count++;
            }
        }
        // a SUPERSEDE "by" must name an intent that exists or is created now
        d.intents.removeIf(item -> {
            if (!"SUPERSEDE".equals(item.op)) {
                return false;
            }
            boolean known = refs.contains(item.by) || (id(item.by) != null && ctx.intents().containsKey(id(item.by)));
            if (!known) {
                d.notes.add("intents: SUPERSEDE i" + item.id + " by unknown '" + item.by + "' dropped");
            }
            return !known;
        });
    }

    private static DirectorDecision.IntentOp create(JsonNode node, DirectorDecision d, Context ctx, Set<String> refs) {
        DirectorDecision.IntentOp item = new DirectorDecision.IntentOp();
        item.op = "CREATE";
        item.ref = node.path("ref").asText("").trim();
        if (!item.ref.matches("n\\d{1,3}") || !refs.add(item.ref)) {
            d.notes.add("intents: CREATE with missing or repeated ref '" + item.ref + "' dropped");
            return null;
        }
        item.participant = node.path("participant").asText("").trim().toUpperCase(Locale.ROOT);
        if (!ctx.partnersPresent().contains(item.participant)) {
            d.notes.add("intents: " + item.ref + " has no valid partner, dropped");
            refs.remove(item.ref);
            return null;
        }
        item.kind = oneOf(node.path("kind").asText(""), INTENT_KINDS, "WANT");
        item.quote = clip(node.path("quote").asText(""), 400);
        item.interpretation = clip(node.path("interpretation").asText(""), 500);
        item.epistemic = oneOf(node.path("epistemic").asText(""), EPISTEMIC, "INFERRED");
        item.confidence = clamp(node.path("confidence").asDouble(0.5));
        item.importance = oneOf(node.path("importance").asText(""), IMPORTANCE, "MEDIUM");
        item.evidence = messageIds(node.path("evidence"), ctx);
        item.alternatives = strings(node.path("alternatives"), 4, 12);
        if (!item.quote.isBlank() && !quoteFound(item.quote, item.participant, ctx)) {
            if ("STATED".equals(item.epistemic)) {
                item.epistemic = "INFERRED";
                d.notes.add("intents: " + item.ref + " quote not found in " + item.participant
                        + "'s messages, STATED became INFERRED");
            } else {
                d.notes.add("intents: " + item.ref + " quote not found in " + item.participant + "'s messages");
            }
        }
        if ("STATED".equals(item.epistemic) && item.quote.isBlank()) {
            item.epistemic = "INFERRED";
            d.notes.add("intents: " + item.ref + " STATED without a quote became INFERRED");
        }
        Long parent = id(node.path("parent").asText(""));
        if (parent != null) {
            IntentView view = ctx.intents().get(parent);
            if (view == null || !item.participant.equals(view.role())) {
                d.notes.add("intents: " + item.ref + " parent i" + parent + " unknown or of the other partner, ignored");
            } else {
                item.parent = parent;
                item.change = oneOf(node.path("change").asText(""), Set.of("ELABORATION", "TRUE_CHANGE"), "ELABORATION");
            }
        }
        return item;
    }

    private static DirectorDecision.IntentOp status(JsonNode node, DirectorDecision d, Context ctx) {
        DirectorDecision.IntentOp item = new DirectorDecision.IntentOp();
        item.op = "STATUS";
        item.id = id(node.path("id").asText(""));
        IntentView view = item.id == null ? null : ctx.intents().get(item.id);
        if (view == null) {
            d.notes.add("intents: STATUS for unknown intent '" + node.path("id").asText("") + "' dropped");
            return null;
        }
        item.participant = view.role();
        item.status = oneOf(node.path("status").asText(""), Set.of("CONFIRMED", "REJECTED", "UNCONFIRMED"), "");
        if (item.status.isEmpty()) {
            d.notes.add("intents: STATUS i" + item.id + " without a valid status dropped");
            return null;
        }
        item.reason = clip(node.path("reason").asText(""), 300);
        item.evidence = messageIds(node.path("evidence"), ctx);
        if ("CONFIRMED".equals(item.status)) {
            boolean fromPartner = item.evidence.stream().anyMatch(id -> view.role().equals(ctx.roleByMessageId().get(id)));
            if (!fromPartner) {
                d.notes.add("intents: CONFIRMED i" + item.id + " needs evidence from " + view.role()
                        + "'s own messages, dropped");
                return null;
            }
        }
        return item;
    }

    private static DirectorDecision.IntentOp supersede(JsonNode node, DirectorDecision d, Context ctx) {
        DirectorDecision.IntentOp item = new DirectorDecision.IntentOp();
        item.op = "SUPERSEDE";
        item.id = id(node.path("id").asText(""));
        IntentView view = item.id == null ? null : ctx.intents().get(item.id);
        if (view == null) {
            d.notes.add("intents: SUPERSEDE of unknown intent dropped");
            return null;
        }
        item.participant = view.role();
        item.by = node.path("by").asText("").trim();
        item.change = oneOf(node.path("change").asText(""), Set.of("ELABORATION", "TRUE_CHANGE"), "ELABORATION");
        item.reason = clip(node.path("reason").asText(""), 300);
        return item;
    }

    private static DirectorDecision.IntentOp priority(JsonNode node, DirectorDecision d, Context ctx) {
        DirectorDecision.IntentOp item = new DirectorDecision.IntentOp();
        item.op = "PRIORITY";
        item.id = id(node.path("id").asText(""));
        IntentView view = item.id == null ? null : ctx.intents().get(item.id);
        if (view == null) {
            d.notes.add("intents: PRIORITY for unknown intent dropped");
            return null;
        }
        item.participant = view.role();
        item.importance = oneOf(node.path("importance").asText(""), IMPORTANCE, "MEDIUM");
        item.reason = clip(node.path("reason").asText(""), 300);
        return item;
    }

    /**
     * A stage of a partner's path is reached in order (W, D, E, P), once, and the
     * first one only when that partner has a confirmed reading (a want confirmed
     * by them, now or before).
     */
    private static void readMilestones(JsonNode array, DirectorDecision d, Context ctx) {
        if (!array.isArray()) {
            return;
        }
        Map<String, Set<String>> reached = new java.util.HashMap<>();
        if (ctx.reachedStages() != null) {
            ctx.reachedStages().forEach((role, stages) -> reached.put(role, new LinkedHashSet<>(stages)));
        }
        for (JsonNode node : array) {
            DirectorDecision.MilestoneOp op = new DirectorDecision.MilestoneOp();
            op.participant = node.path("participant").asText("").trim().toUpperCase(Locale.ROOT);
            op.stage = node.path("stage").asText("").trim().toUpperCase(Locale.ROOT);
            op.note = clip(node.path("note").asText(""), 400);
            Set<String> mine = reached.computeIfAbsent(op.participant, key -> new LinkedHashSet<>());
            int index = STAGES.indexOf(op.stage);
            String refusal = null;
            if (!ctx.partnersPresent().contains(op.participant) || index < 0) {
                refusal = "no valid partner or stage";
            } else if (mine.contains(op.stage)) {
                refusal = "already reached";
            } else if (d.milestones.stream().anyMatch(done -> done.participant.equals(op.participant))) {
                refusal = "one stage per partner per turn";
            } else if (index > 0 && !mine.contains(STAGES.get(index - 1))) {
                refusal = "stage " + STAGES.get(index - 1) + " is not reached yet";
            } else if (index == 0 && !hasConfirmedReading(op.participant, d, ctx)) {
                refusal = "no reading of this partner is confirmed by them";
            } else if (op.note.isBlank()) {
                refusal = "no note for the person";
            }
            if (refusal != null) {
                d.notes.add("milestones: " + op.participant + " " + op.stage + " refused: " + refusal);
                continue;
            }
            mine.add(op.stage);
            d.milestones.add(op);
        }
    }

    private static boolean hasConfirmedReading(String role, DirectorDecision d, Context ctx) {
        boolean before = ctx.intents().values().stream()
                .anyMatch(view -> role.equals(view.role()) && "CONFIRMED".equals(view.status()));
        boolean now = d.intents.stream().anyMatch(op -> "STATUS".equals(op.op) && "CONFIRMED".equals(op.status)
                && op.id != null && ctx.intents().containsKey(op.id) && role.equals(ctx.intents().get(op.id).role()));
        return before || now;
    }

    private static void readPlans(JsonNode array, DirectorDecision d, Context ctx) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode node : array) {
            if (d.plans.size() >= 4) {
                break;
            }
            DirectorDecision.PlanOp plan = new DirectorDecision.PlanOp();
            plan.op = node.path("op").asText("").trim().toUpperCase(Locale.ROOT);
            plan.note = clip(node.path("note").asText(""), 300);
            if ("AGREE".equals(plan.op)) {
                plan.participant = node.path("participant").asText("").trim().toUpperCase(Locale.ROOT);
                plan.action = clip(node.path("action").asText(""), 300);
                if (!ctx.partnersPresent().contains(plan.participant) || plan.action.isBlank()) {
                    d.notes.add("plans: AGREE without partner or action dropped");
                    continue;
                }
            } else if ("UPDATE".equals(plan.op)) {
                plan.id = node.path("id").asText("").trim();
                plan.status = oneOf(node.path("status").asText(""), PLAN_STATUS, "");
                if (!ctx.planIds().contains(plan.id) || plan.status.isEmpty()) {
                    d.notes.add("plans: UPDATE of unknown plan '" + plan.id + "' or without status dropped");
                    continue;
                }
            } else {
                d.notes.add("plans: unknown op '" + plan.op + "' ignored");
                continue;
            }
            d.plans.add(plan);
        }
    }

    private static void readDecision(JsonNode n, DirectorDecision d, Context ctx) {
        String action = n.path("action").asText("").trim().toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            d.notes.add("decision: unknown action '" + action + "' treated as OTHER");
            d.actionLabel = clip(action, 40);
            action = "OTHER";
        }
        d.action = action;
        if ("OTHER".equals(action) && d.actionLabel.isBlank()) {
            d.actionLabel = clip(n.path("action_label").asText(""), 40);
        }
        d.addressee = oneOf(n.path("addressee").asText(""), ADDRESSEES, ctx.defaultAddressee());
        if (!"BOTH".equals(d.addressee) && !ctx.partnersPresent().contains(d.addressee)) {
            d.notes.add("decision: addressee " + d.addressee + " is not in the room");
            d.addressee = ctx.defaultAddressee();
        }
        d.purpose = clip(n.path("purpose").asText(""), 300);
        d.rationale = clip(n.path("rationale").asText(""), 600);
        for (String rule : strings(n.path("rules_used"), 10, 20)) {
            String upper = rule.trim().toUpperCase(Locale.ROOT);
            if (RULE_ID.matcher(upper).matches()) {
                d.rulesUsed.add(upper);
            }
        }
        JsonNode alternatives = n.path("alternatives");
        if (alternatives.isArray()) {
            for (JsonNode alternative : alternatives) {
                if (d.alternatives.size() >= 4) {
                    break;
                }
                String text = alternative.isObject()
                        ? alternative.path("action").asText("") + ": " + alternative.path("why_not").asText("")
                        : alternative.asText("");
                if (!text.isBlank()) {
                    d.alternatives.add(clip(text, 200));
                }
            }
        }
        JsonNode governance = n.path("governance");
        d.gov = oneOf(governance.path("gov").asText(""), GOV, "NONE");
        d.govTarget = oneOf(governance.path("target").asText(""), TARGETS, "NONE");
        d.govDuration = Math.max(0, Math.min(600, governance.path("duration").asInt(0)));
        d.react = normalizeToken(n.path("signal").path("react").asText(null));
        d.sticker = normalizeToken(n.path("signal").path("sticker").asText(null));
        d.lead = oneOf(n.path("lead").asText(""), LEADS, "NONE");
        d.route = "WAIT".equals(d.action) ? "SILENT" : "SPEAK";
    }

    /** TEACH needs a lesson of kb/lessons.json; without one the move becomes an ordinary reply. */
    private static void readLesson(JsonNode lesson, DirectorDecision d) {
        String id = lesson.path("id").asText("").trim().toUpperCase(Locale.ROOT);
        if (!"TEACH".equals(d.action)) {
            if (!id.isEmpty()) {
                d.notes.add("lesson: " + id + " ignored, the action is " + d.action);
            }
            return;
        }
        if (Lessons.find(id).isEmpty()) {
            d.notes.add("lesson: TEACH without a known lesson ('" + clip(id, 40) + "') became RESPOND");
            d.action = "RESPOND";
            return;
        }
        d.lessonId = id;
        d.lessonWhy = clip(lesson.path("why").asText(""), 300);
    }

    private static void readQuestion(JsonNode q, DirectorDecision d, Context ctx) {
        boolean asked = q.isObject();
        if (!asked) {
            if ("ASK_CLARIFYING".equals(d.action)) {
                downgradeQuestion(d, "ASK_CLARIFYING without a question object");
            }
            return;
        }
        DirectorDecision.QuestionSpec spec = new DirectorDecision.QuestionSpec();
        spec.type = oneOf(q.path("type").asText(""), Set.of("CHOOSE", "CONFIRM"), "");
        spec.addressee = q.path("addressee").asText("").trim().toUpperCase(Locale.ROOT);
        spec.reason = clip(q.path("reason").asText(""), 300);
        spec.impact = oneOf(q.path("impact").asText(""), IMPORTANCE, "MEDIUM");
        if (spec.type.isEmpty() || !ctx.partnersPresent().contains(spec.addressee)) {
            downgradeQuestion(d, "question without a valid type or addressee");
            return;
        }
        Set<String> readings = new LinkedHashSet<>();
        for (String raw : strings(q.path("intents"), 6, 12)) {
            String value = raw.trim();
            if (value.matches("n\\d{1,3}")) {
                boolean created = d.intents.stream().anyMatch(op -> "CREATE".equals(op.op)
                        && value.equals(op.ref) && spec.addressee.equals(op.participant));
                if (created) {
                    readings.add(value);
                }
            } else {
                Long intentId = id(value);
                IntentView view = intentId == null ? null : ctx.intents().get(intentId);
                if (view != null && spec.addressee.equals(view.role()) && "UNCONFIRMED".equals(view.status())) {
                    readings.add("i" + intentId);
                }
            }
        }
        spec.intents = new ArrayList<>(readings);
        String refusal = null;
        if ("CHOOSE".equals(spec.type) && (spec.intents.size() < 2 || spec.intents.size() > 4)) {
            refusal = "CHOOSE needs 2 to 4 unconfirmed readings of the addressee, got " + spec.intents.size();
        } else if ("CONFIRM".equals(spec.type) && spec.intents.isEmpty()) {
            refusal = "CONFIRM needs one unconfirmed reading of the addressee";
        } else if (ctx.openQuestions().values().stream().anyMatch(open -> open.role().equals(spec.addressee)
                && d.questionUpdates.stream().noneMatch(update -> open.id().equals(update.id)))) {
            refusal = "the addressee already has an open question";
        } else if (ctx.questionsThisSession() >= ctx.maxQuestionsPerSession()) {
            refusal = "the session already used " + ctx.questionsThisSession() + " structured questions";
        }
        if (refusal != null) {
            downgradeQuestion(d, refusal);
            return;
        }
        if ("CONFIRM".equals(spec.type) && spec.intents.size() > 1) {
            spec.intents = new ArrayList<>(spec.intents.subList(0, 1));
        }
        d.question = spec;
        d.action = "ASK_CLARIFYING";
        d.route = "SPEAK";
        d.addressee = spec.addressee;
    }

    private static void readQuestionUpdates(JsonNode array, DirectorDecision d, Context ctx) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode node : array) {
            DirectorDecision.QuestionUpdate update = new DirectorDecision.QuestionUpdate();
            update.id = id(node.path("id").asText(""));
            update.status = oneOf(node.path("status").asText(""), Set.of("ANSWERED_IN_TEXT", "EXPIRED"), "");
            update.reason = clip(node.path("reason").asText(""), 300);
            if (update.id == null || !ctx.openQuestions().containsKey(update.id) || update.status.isEmpty()) {
                d.notes.add("question_updates: '" + node.path("id").asText("") + "' is not an open question, ignored");
                continue;
            }
            d.questionUpdates.add(update);
        }
    }

    private static void readBrief(JsonNode b, DirectorDecision d, Context ctx) {
        d.briefDo = clip(b.path("do").asText(""), 700);
        d.briefMustNot = strings(b.path("must_not"), 5, 200);
        d.briefLength = oneOf(b.path("length").asText(""), Set.of("SHORT", "MEDIUM"), "SHORT");
        d.briefReferTo = messageIds(b.path("refer_to"), ctx);
    }

    private static void readSafety(JsonNode s, DirectorDecision d, Context ctx) {
        d.safetyLevel = oneOf(s.path("level").asText(""), SAFETY, "NONE");
        d.safetySignals = strings(s.path("signals"), 5, 120);
        d.safetyWho = oneOf(s.path("who").asText(""), Set.of("OWNER", "MEMBER", "NONE"), "NONE");
        d.safetyNote = clip(s.path("note").asText(""), 400);
        if (rank(d.safetyLevel) < rank(ctx.currentSafetyLevel())) {
            d.notes.add("safety: level lowered from " + ctx.currentSafetyLevel() + " to " + d.safetyLevel);
        }
    }

    // ------------------------------------------------------------ guarantees

    private static void enforceSafety(DirectorDecision d, Context ctx) {
        int level = rank(d.safetyLevel);
        if (level < rank("CONCERN")) {
            return;
        }
        if (d.question != null) {
            d.question = null;
            d.notes.add("safety: no structured question at " + d.safetyLevel);
        }
        if ("URGENT".equals(d.safetyLevel) && !d.action.startsWith("SAFETY_")) {
            d.notes.add("safety: " + d.action + " replaced by SAFETY_RESPONSE at URGENT");
            d.action = "SAFETY_RESPONSE";
        } else if (ROUTINE.contains(d.action)) {
            d.notes.add("safety: routine " + d.action + " replaced by SAFETY_ASSESS at CONCERN");
            d.action = "SAFETY_ASSESS";
        }
        if (d.action.startsWith("SAFETY_") && PartnerRole.isPartner(d.safetyWho)) {
            d.addressee = d.safetyWho;
        }
        boolean pausesTheDiscloser = ("CAUTION".equals(d.gov) || "PAUSE_USER".equals(d.gov))
                && d.safetyWho.equals(d.govTarget);
        if (pausesTheDiscloser || "PAUSE_BOTH".equals(d.gov)) {
            d.notes.add("safety: " + d.gov + " on " + d.govTarget + " removed during a safety concern");
            d.gov = "NONE";
            d.govTarget = "NONE";
            d.govDuration = 0;
        }
        if (d.plans.removeIf(plan -> "AGREE".equals(plan.op))) {
            d.notes.add("safety: no new couple plan during a safety concern");
        }
        d.route = "SPEAK";
        d.react = null;
        d.sticker = null;
    }

    private static void enforceConsistency(DirectorDecision d, Context ctx) {
        if ("END_SESSION".equals(d.action) && !"END_SESSION".equals(d.gov)) {
            // the platform owns the clock: it offers the break and closes sessions, the counselor does not
            d.notes.add("decision: END_SESSION without gov END_SESSION became RESPOND; the platform ends sessions");
            d.action = "RESPOND";
            d.briefDo = "Respond briefly and warmly to the latest message and keep the conversation open.";
            d.briefMustNot.add("do not say that the time is over, that the session ends or that you stop for today");
        }
        d.route = "WAIT".equals(d.action) ? "SILENT" : "SPEAK";
        if (!d.silent()) {
            d.react = null;
            d.sticker = null;
            if (d.briefDo.isBlank()) {
                d.briefDo = "Respond briefly and warmly to the latest message.";
                d.notes.add("brief: empty, a neutral brief was used");
            }
        } else if (d.react != null) {
            d.sticker = null;
        }
        if ("PAUSE_USER".equals(d.gov) || "CAUTION".equals(d.gov)) {
            if (!PartnerRole.isPartner(d.govTarget) || !ctx.partnersPresent().contains(d.govTarget)) {
                d.notes.add("governance: " + d.gov + " without a partner target removed");
                d.gov = "NONE";
                d.govTarget = "NONE";
                d.govDuration = 0;
            }
        } else if ("PAUSE_BOTH".equals(d.gov)) {
            d.govTarget = "BOTH";
        } else if ("NONE".equals(d.gov)) {
            d.govTarget = "NONE";
            d.govDuration = 0;
        }
    }

    private static void downgradeQuestion(DirectorDecision d, String reason) {
        d.question = null;
        if ("ASK_CLARIFYING".equals(d.action)) {
            d.action = "ASK_OPEN";
            d.briefDo = (d.briefDo.isBlank() ? "" : d.briefDo + " ")
                    + "Ask one short, natural open question instead of giving options.";
        }
        d.notes.add("question refused: " + reason);
    }

    // --------------------------------------------------------------- helpers

    static int rank(String level) {
        return switch (level == null ? "NONE" : level) {
            case "WATCH" -> 1;
            case "CONCERN" -> 2;
            case "URGENT" -> 3;
            default -> 0;
        };
    }

    private static boolean quoteFound(String quote, String role, Context ctx) {
        // spaces are ignored: «باهم» and «با هم» are the same words
        String needle = PersianText.loose(quote).replace(" ", "");
        if (needle.isEmpty()) {
            return false;
        }
        for (String text : ctx.looseTextsByRole().getOrDefault(role, List.of())) {
            if (text.replace(" ", "").contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<Long> messageIds(JsonNode array, Context ctx) {
        List<Long> ids = new ArrayList<>();
        if (!array.isArray()) {
            return ids;
        }
        for (JsonNode node : array) {
            Long value = id(node.asText(""));
            if (value != null && ctx.roleByMessageId().containsKey(value) && !ids.contains(value)) {
                ids.add(value);
            }
            if (ids.size() >= 6) {
                break;
            }
        }
        return ids;
    }

    /** "i31", "q7", "#1203", "d12" or "31" to 31. */
    static Long id(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = ID.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeToken(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return com.coupleai.coupleai.beinema.governance.CounselorSignal.normalize(raw);
    }

    private static String oneOf(String raw, Set<String> allowed, String fallback) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(value) ? value : fallback;
    }

    private static List<String> upperList(JsonNode array, Set<String> allowed, int max) {
        List<String> values = new ArrayList<>();
        if (!array.isArray()) {
            return values;
        }
        for (JsonNode node : array) {
            String value = node.asText("").trim().toUpperCase(Locale.ROOT);
            if (allowed.contains(value) && !values.contains(value)) {
                values.add(value);
            }
            if (values.size() >= max) {
                break;
            }
        }
        return values;
    }

    private static List<String> strings(JsonNode array, int max, int maxLength) {
        List<String> values = new ArrayList<>();
        if (!array.isArray()) {
            return values;
        }
        for (JsonNode node : array) {
            if (node.isNull()) {
                continue;
            }
            String value = clip(node.asText(""), maxLength);
            if (!value.isBlank()) {
                values.add(value);
            }
            if (values.size() >= max) {
                break;
            }
        }
        return values;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.5;
        }
        return Math.max(0, Math.min(1, value));
    }

    private static String clip(String value, int max) {
        if (value == null || "null".equals(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
