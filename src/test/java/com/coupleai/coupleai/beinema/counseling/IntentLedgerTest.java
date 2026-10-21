package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Enum.IntentEpistemic;
import com.coupleai.coupleai.beinema.Enum.IntentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentLedgerTest {

    private final FakeRoom f = new FakeRoom();
    private final Map<String, Long> users = Map.of("OWNER", 1L, "MEMBER", 3L);

    private static DirectorDecision.IntentOp create(String ref, String epistemic, String quote, String meaning) {
        DirectorDecision.IntentOp op = new DirectorDecision.IntentOp();
        op.op = "CREATE";
        op.ref = ref;
        op.participant = "OWNER";
        op.kind = "WANT";
        op.quote = quote;
        op.interpretation = meaning;
        op.epistemic = epistemic;
        return op;
    }

    @Test
    void wordsOfThePartnerAreConfirmedReadingsAreNot() {
        Map<String, Long> refs = f.ledger.apply(FakeRoom.ROOM, users, List.of(
                create("n1", "STATED", "می‌خوام شب‌ها آروم حرف بزنیم", "wants calm talks at night"),
                create("n2", "HYPOTHESIS", "", "wants him home earlier")), 50L, 1);

        ClientIntent stated = f.intents.get(refs.get("n1"));
        ClientIntent guessed = f.intents.get(refs.get("n2"));
        assertEquals(IntentStatus.CONFIRMED, stated.getStatus());
        assertEquals(IntentEpistemic.STATED, stated.getEpistemic());
        assertEquals(IntentStatus.UNCONFIRMED, guessed.getStatus());
        assertEquals("d50", guessed.getChangedBy());
    }

    @Test
    void aChangeKeepsTheHistoryChain() {
        Map<String, Long> first = f.ledger.apply(FakeRoom.ROOM, users,
                List.of(create("n1", "STATED", "کمتر دعوا کنیم", "fewer fights")), 50L, 1);
        Long fewerFights = first.get("n1");

        DirectorDecision.IntentOp precise = create("n1", "STATED", "شب‌ها آروم حرف بزنیم", "calm talks in the evening");
        precise.parent = fewerFights;
        precise.change = "ELABORATION";
        Long calmTalks = f.ledger.apply(FakeRoom.ROOM, users, List.of(precise), 51L, 1).get("n1");
        assertEquals(IntentStatus.REFINED, f.intents.get(fewerFights).getStatus());
        assertEquals(fewerFights, f.intents.get(calmTalks).getParentId());

        DirectorDecision.IntentOp separate = create("n1", "STATED", "می‌خوام به جدایی فکر کنم", "wants to consider separating");
        DirectorDecision.IntentOp supersede = new DirectorDecision.IntentOp();
        supersede.op = "SUPERSEDE";
        supersede.id = calmTalks;
        supersede.by = "n1";
        supersede.change = "TRUE_CHANGE";
        supersede.reason = "she said the fights no longer matter";
        Long separating = f.ledger.apply(FakeRoom.ROOM, users, List.of(separate, supersede), 52L, 2).get("n1");

        assertEquals(IntentStatus.SUPERSEDED, f.intents.get(calmTalks).getStatus());
        assertTrue(f.intents.get(calmTalks).getStatusReason().contains("i" + separating));
        assertEquals(calmTalks, f.intents.get(separating).getParentId());
        assertEquals("TRUE_CHANGE", f.intents.get(separating).getChangeKind());
        assertEquals(3, f.intents.size(), "nothing was deleted");
    }

    @Test
    void noneRejectsTheReadingsAndNotSureLeavesThemOpen() {
        Map<String, Long> refs = f.ledger.apply(FakeRoom.ROOM, users, List.of(
                create("n1", "HYPOTHESIS", "", "a"), create("n2", "HYPOTHESIS", "", "b")), 50L, 1);
        ClarifyingQuestion question = new ClarifyingQuestion();
        question.setId(7L);
        question.setTargetIntentIds(refs.get("n1") + "," + refs.get("n2"));

        IntentLedger.Resolution unsure = f.ledger.resolveFromAnswer(question,
                new QuestionOptions.Option("unsure", "مطمئن نیستم", QuestionOptions.UNSURE, null, false));
        assertTrue(unsure.confirmed().isEmpty() && unsure.rejected().isEmpty());
        assertEquals(IntentStatus.UNCONFIRMED, f.intents.get(refs.get("n1")).getStatus());

        IntentLedger.Resolution none = f.ledger.resolveFromAnswer(question,
                new QuestionOptions.Option("none", "هیچ‌کدوم", QuestionOptions.NONE, null, true));
        assertEquals(2, none.rejected().size());
        assertEquals(IntentStatus.REJECTED, f.intents.get(refs.get("n2")).getStatus());
        assertEquals("q7", f.intents.get(refs.get("n2")).getChangedBy());
    }
}
