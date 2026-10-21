package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotUpdaterTest {

    @Test
    void movesPlansSafetyAndGovernanceAreRememberedWithReasons() {
        CounselingSnapshot memory = new CounselingSnapshot();
        DirectorDecision first = new DirectorDecision();
        first.action = "ASK_OPEN";
        first.addressee = "OWNER";
        first.focus.put("OWNER", "W");
        first.focusReason = "her want is unclear";
        first.knownAdd = List.of("they argue at night");
        SnapshotUpdater.apply(memory, first, 10L, 1);

        DirectorDecision second = new DirectorDecision();
        second.action = "SUPPORT_PLANNING";
        second.addressee = "OWNER";
        second.focus.put("OWNER", "P");
        second.focusReason = "she evaluated her nagging as not working";
        second.previousEffect = "HELPFUL";
        DirectorDecision.PlanOp plan = new DirectorDecision.PlanOp();
        plan.op = "AGREE";
        plan.participant = "OWNER";
        plan.action = "ask once, calmly, after dinner";
        second.plans.add(plan);
        second.gov = "CAUTION";
        second.govTarget = "MEMBER";
        second.knownAdd = List.of("They argue at night");
        SnapshotUpdater.apply(memory, second, 11L, 1);

        assertEquals("P", memory.focusOf("OWNER"));
        assertEquals(2, memory.transitions.size());
        assertEquals("she evaluated her nagging as not working", memory.transitions.get(1).reason);
        assertEquals("p1", memory.plans.get(0).id);
        assertEquals("AGREED", memory.plans.get(0).status);
        assertEquals("HELPFUL", memory.lastMoves.get(0).effect, "the first move's effect comes from the second decision");
        assertEquals(1, memory.governance.size());
        assertEquals(1, memory.known.size(), "the same fact is not added twice");

        DirectorDecision third = new DirectorDecision();
        DirectorDecision.PlanOp done = new DirectorDecision.PlanOp();
        done.op = "UPDATE";
        done.id = "p1";
        done.status = "NOT_DONE";
        done.note = "forgot";
        third.plans.add(done);
        third.safetyLevel = "WATCH";
        third.safetySignals = List.of("afraid of his anger");
        SnapshotUpdater.apply(memory, third, 12L, 2);
        assertEquals("NOT_DONE", memory.plans.get(0).status);
        assertEquals("WATCH", memory.safety.level);
        assertTrue(memory.safety.signals.contains("afraid of his anger"));
    }
}
