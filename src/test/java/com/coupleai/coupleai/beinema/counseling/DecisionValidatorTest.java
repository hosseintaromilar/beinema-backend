package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String OWNER_SAID = "من دیگه نمی‌خوام هر شب سر این موضوع با هم بحث کنیم";

    private Map<Long, DecisionValidator.IntentView> intents = new LinkedHashMap<>();
    private Map<Long, DecisionValidator.QuestionView> open = new LinkedHashMap<>();
    private int asked = 0;
    private Map<String, Set<String>> reached = new LinkedHashMap<>();
    private String currentSafety = "NONE";

    private DecisionValidator.Context ctx() {
        return new DecisionValidator.Context(
                Set.of("OWNER", "MEMBER"),
                "OWNER",
                Map.of("OWNER", List.of(PersianText.loose(OWNER_SAID)), "MEMBER", List.of(PersianText.loose("من خسته شدم"))),
                Map.of(1203L, "OWNER", 1204L, "MEMBER", 1199L, "COUNSELOR"),
                intents,
                open,
                Set.of("p1"),
                asked,
                3,
                false,
                currentSafety,
                reached
        );
    }

    private static JsonNode json(String decision, String extra) throws Exception {
        return JSON.readTree("{\"schema\":\"director.v1\",\"analysis\":{\"speaker\":\"OWNER\",\"kinds\":[\"WANT\"]},"
                + "\"situation\":{\"focus\":{\"OWNER\":\"W\",\"MEMBER\":\"NONE\"}},"
                + "\"decision\":" + decision
                + ",\"brief\":{\"do\":\"say it\",\"length\":\"SHORT\",\"refer_to\":[1203, 999]}"
                + (extra.isEmpty() ? "" : "," + extra) + "}");
    }

    private static final String CHOOSE_INTENTS = "\"intents\":["
            + "{\"op\":\"CREATE\",\"ref\":\"n1\",\"participant\":\"OWNER\",\"kind\":\"WANT\",\"quote\":\"نمی‌خوام هر شب سر این موضوع با هم بحث کنیم\",\"interpretation\":\"wants the fights to stop\",\"epistemic\":\"HYPOTHESIS\",\"confidence\":0.4,\"importance\":\"HIGH\",\"evidence\":[1203]},"
            + "{\"op\":\"CREATE\",\"ref\":\"n2\",\"participant\":\"OWNER\",\"kind\":\"WANT\",\"quote\":\"\",\"interpretation\":\"wants him to change\",\"epistemic\":\"HYPOTHESIS\",\"confidence\":0.3,\"importance\":\"HIGH\"},"
            + "{\"op\":\"CREATE\",\"ref\":\"n3\",\"participant\":\"OWNER\",\"kind\":\"WANT\",\"quote\":\"\",\"interpretation\":\"doubts the relationship\",\"epistemic\":\"HYPOTHESIS\",\"confidence\":0.3,\"importance\":\"HIGH\"}]";
    private static final String CHOOSE_QUESTION = "\"question\":{\"type\":\"CHOOSE\",\"addressee\":\"OWNER\",\"intents\":[\"n1\",\"n2\",\"n3\"],\"reason\":\"decides the route\",\"impact\":\"HIGH\"}";
    private static final String ASK = "{\"action\":\"ASK_CLARIFYING\",\"addressee\":\"OWNER\",\"governance\":{\"gov\":\"NONE\"}}";

    @Test
    void aRealQuoteStaysStatedAnInventedOneBecomesInferred() throws Exception {
        String ops = "\"intents\":["
                + "{\"op\":\"CREATE\",\"ref\":\"n1\",\"participant\":\"OWNER\",\"kind\":\"WANT\",\"quote\":\"نمیخوام هر شب سر این موضوع باهم بحث کنیم\",\"interpretation\":\"x\",\"epistemic\":\"STATED\"},"
                + "{\"op\":\"CREATE\",\"ref\":\"n2\",\"participant\":\"MEMBER\",\"kind\":\"WANT\",\"quote\":\"می‌خوام بیشتر دیده بشم\",\"interpretation\":\"y\",\"epistemic\":\"STATED\"},"
                + "{\"op\":\"CREATE\",\"ref\":\"n3\",\"participant\":\"PARTNER_C\",\"kind\":\"WANT\",\"quote\":\"\",\"interpretation\":\"z\",\"epistemic\":\"INFERRED\"}]";
        DirectorDecision d = DecisionValidator.validate(json("{\"action\":\"REFLECT\",\"addressee\":\"OWNER\"}", ops), ctx());

        assertEquals(2, d.intents.size(), "an intent of a partner who is not in the room is dropped");
        assertEquals("STATED", d.intents.get(0).epistemic, "spacing and ZWNJ differences still match");
        assertEquals("INFERRED", d.intents.get(1).epistemic, "a quote MEMBER never wrote is not STATED");
        assertTrue(d.notes.stream().anyMatch(note -> note.contains("quote not found")));
        assertEquals(List.of(1203L), d.briefReferTo, "unknown message ids are removed");
    }

    @Test
    void aStageOfThePathIsReachedInOrderAndTheWantOnlyWhenConfirmed() throws Exception {
        String early = "\"milestones\":[{\"participant\":\"OWNER\",\"stage\":\"W\",\"note\":\"خواسته‌ات روشن شد\"},"
                + "{\"participant\":\"MEMBER\",\"stage\":\"D\",\"note\":\"x\"}]";
        DirectorDecision d = DecisionValidator.validate(json("{\"action\":\"RESPOND\",\"addressee\":\"OWNER\"}", early), ctx());
        assertTrue(d.milestones.isEmpty(), "no confirmed want yet, and D cannot come before W");

        intents.put(31L, new DecisionValidator.IntentView(31L, "OWNER", "CONFIRMED"));
        DirectorDecision now = DecisionValidator.validate(json("{\"action\":\"RESPOND\",\"addressee\":\"OWNER\"}",
                "\"milestones\":[{\"participant\":\"OWNER\",\"stage\":\"W\",\"note\":\"خواسته‌ات روشن شد\"}]"), ctx());
        assertEquals(1, now.milestones.size());

        reached.put("OWNER", Set.of("W"));
        DirectorDecision again = DecisionValidator.validate(json("{\"action\":\"RESPOND\",\"addressee\":\"OWNER\"}",
                "\"milestones\":[{\"participant\":\"OWNER\",\"stage\":\"W\",\"note\":\"x\"}]"), ctx());
        assertTrue(again.milestones.isEmpty(), "a stage is reached once");
    }

    @Test
    void theCounselorCannotEndTheSessionBecauseOfTime() throws Exception {
        DirectorDecision d = DecisionValidator.validate(
                json("{\"action\":\"END_SESSION\",\"addressee\":\"BOTH\",\"governance\":{\"gov\":\"NONE\"}}", ""), ctx());

        assertEquals("RESPOND", d.action, "the platform offers the break and closes sessions");
        assertTrue(d.briefMustNot.stream().anyMatch(item -> item.contains("time is over")));
        assertTrue(d.notes.stream().anyMatch(note -> note.contains("END_SESSION")));

        DirectorDecision governed = DecisionValidator.validate(
                json("{\"action\":\"END_SESSION\",\"addressee\":\"BOTH\",\"governance\":{\"gov\":\"END_SESSION\",\"target\":\"BOTH\"}}", ""), ctx());
        assertEquals("END_SESSION", governed.action, "a governance end for danger stays");
    }

    @Test
    void aConfirmationNeedsEvidenceFromThatPartner() throws Exception {
        intents.put(31L, new DecisionValidator.IntentView(31L, "OWNER", "UNCONFIRMED"));
        String ops = "\"intents\":["
                + "{\"op\":\"STATUS\",\"id\":\"i31\",\"status\":\"CONFIRMED\",\"reason\":\"he said so\",\"evidence\":[1204]},"
                + "{\"op\":\"STATUS\",\"id\":\"i31\",\"status\":\"CONFIRMED\",\"reason\":\"she said so\",\"evidence\":[1203]}]";
        DirectorDecision d = DecisionValidator.validate(json("{\"action\":\"REFLECT\"}", ops), ctx());

        assertEquals(1, d.intents.size(), "the confirmation backed only by the other partner is dropped");
        assertEquals(List.of(1203L), d.intents.get(0).evidence);
    }

    @Test
    void aChooseQuestionWithThreeDistinctReadingsIsAccepted() throws Exception {
        DirectorDecision d = DecisionValidator.validate(json(ASK, CHOOSE_INTENTS + "," + CHOOSE_QUESTION), ctx());

        assertNotNull(d.question);
        assertEquals("ASK_CLARIFYING", d.action);
        assertEquals("OWNER", d.addressee);
        assertEquals(List.of("n1", "n2", "n3"), d.question.intents);
    }

    @Test
    void aQuestionIsRefusedWhenThePartnerAlreadyHasAnOpenOne() throws Exception {
        open.put(7L, new DecisionValidator.QuestionView(7L, "OWNER"));
        DirectorDecision d = DecisionValidator.validate(json(ASK, CHOOSE_INTENTS + "," + CHOOSE_QUESTION), ctx());

        assertNull(d.question);
        assertEquals("ASK_OPEN", d.action);
        assertTrue(d.briefDo.contains("open question"));
        assertTrue(d.notes.stream().anyMatch(note -> note.contains("already has an open question")));
    }

    @Test
    void anOpenQuestionThatTheDirectorClosesDoesNotBlockANewOne() throws Exception {
        open.put(7L, new DecisionValidator.QuestionView(7L, "OWNER"));
        String updates = "\"question_updates\":[{\"id\":\"q7\",\"status\":\"EXPIRED\",\"reason\":\"no longer fits\"}]";
        DirectorDecision d = DecisionValidator.validate(json(ASK, CHOOSE_INTENTS + "," + CHOOSE_QUESTION + "," + updates), ctx());

        assertNotNull(d.question);
        assertEquals(1, d.questionUpdates.size());
    }

    @Test
    void everyHypothesisCanBeCheckedEvenRightAfterTheLastCardOrWhenItSeemsMinor() throws Exception {
        String minor = CHOOSE_QUESTION.replace("\"impact\":\"HIGH\"", "\"impact\":\"LOW\"");
        DirectorDecision d = DecisionValidator.validate(json(ASK, CHOOSE_INTENTS + "," + minor), ctx());
        assertNotNull(d.question, "a reading is never taken for granted; it is checked with a card");
        assertEquals("ASK_CLARIFYING", d.action);
    }

    @Test
    void theSessionQuotaOfQuestionsIsKept() throws Exception {
        asked = 3;
        DirectorDecision d = DecisionValidator.validate(json(ASK, CHOOSE_INTENTS + "," + CHOOSE_QUESTION), ctx());
        assertNull(d.question);
    }

    @Test
    void aChooseQuestionNeedsAtLeastTwoReadingsOfTheAddressee() throws Exception {
        String question = "\"question\":{\"type\":\"CHOOSE\",\"addressee\":\"MEMBER\",\"intents\":[\"n1\",\"n2\"],\"impact\":\"HIGH\"}";
        DirectorDecision d = DecisionValidator.validate(json(ASK, CHOOSE_INTENTS + "," + question), ctx());
        assertNull(d.question, "the readings belong to OWNER, not to MEMBER");
    }

    @Test
    void atConcernTheRoutineStopsAndTheDiscloserIsNeverPaused() throws Exception {
        String decision = "{\"action\":\"FACILITATE_EVALUATION\",\"addressee\":\"MEMBER\",\"governance\":{\"gov\":\"CAUTION\",\"target\":\"OWNER\"}}";
        String extra = CHOOSE_INTENTS + "," + CHOOSE_QUESTION
                + ",\"plans\":[{\"op\":\"AGREE\",\"participant\":\"OWNER\",\"action\":\"talk calmly\"}]"
                + ",\"safety\":{\"level\":\"CONCERN\",\"who\":\"OWNER\",\"signals\":[\"fear\"],\"note\":\"she is afraid\"}";
        DirectorDecision d = DecisionValidator.validate(json(decision, extra), ctx());

        assertNull(d.question);
        assertEquals("SAFETY_ASSESS", d.action);
        assertEquals("OWNER", d.addressee, "speak to the person at risk");
        assertEquals("NONE", d.gov, "no caution for the person who disclosed");
        assertTrue(d.plans.isEmpty(), "no new couple plan during a safety concern");
    }

    @Test
    void urgentAlwaysBecomesASafetyResponse() throws Exception {
        String extra = "\"safety\":{\"level\":\"URGENT\",\"who\":\"MEMBER\"}";
        DirectorDecision d = DecisionValidator.validate(json("{\"action\":\"REFLECT\",\"addressee\":\"OWNER\"}", extra), ctx());
        assertEquals("SAFETY_RESPONSE", d.action);
        assertEquals("MEMBER", d.addressee);
    }

    @Test
    void waitIsSilentWithAValidSignalAndOtherMovesCarryNoSignal() throws Exception {
        DirectorDecision silent = DecisionValidator.validate(json(
                "{\"action\":\"WAIT\",\"signal\":{\"react\":\"listen\",\"sticker\":\"HERE\"}}", ""), ctx());
        assertEquals("SILENT", silent.route);
        assertEquals("LISTEN", silent.react);
        assertNull(silent.sticker, "a reaction is preferred over a sticker");

        DirectorDecision spoken = DecisionValidator.validate(json(
                "{\"action\":\"REFLECT\",\"signal\":{\"react\":\"LISTEN\"}}", ""), ctx());
        assertEquals("SPEAK", spoken.route);
        assertNull(spoken.react);
    }

    @Test
    void unknownValuesAreNeverExecuted() throws Exception {
        String extra = "\"intents\":[{\"op\":\"SUPERSEDE\",\"id\":\"i99\",\"by\":\"n7\",\"change\":\"TRUE_CHANGE\"}]";
        DirectorDecision d = DecisionValidator.validate(json(
                "{\"action\":\"HUG_THEM\",\"governance\":{\"gov\":\"PAUSE_USER\",\"target\":\"NONE\",\"duration\":90}}", extra), ctx());

        assertEquals("OTHER", d.action);
        assertEquals("HUG_THEM", d.actionLabel);
        assertEquals("NONE", d.gov, "a pause without a partner target is removed");
        assertTrue(d.intents.isEmpty(), "an operation on an unknown intent is dropped");
    }

    @Test
    void theFallbackOnlyStaysPresent() {
        DirectorDecision d = DecisionValidator.fallback("MEMBER", "INVALID_JSON");
        assertEquals("LISTENING", d.action);
        assertEquals("MEMBER", d.addressee);
        assertTrue(d.intents.isEmpty());
        assertNull(d.question);
    }
}
