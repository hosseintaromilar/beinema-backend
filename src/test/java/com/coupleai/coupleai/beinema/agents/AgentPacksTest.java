package com.coupleai.coupleai.beinema.agents;

import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.counseling.CounselingConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The packs say what the scattered settings used to say, companion by companion. */
class AgentPacksTest {

    private static final String POURIA = "5ffd2576-20df-422d-af61-f9d1dfff8d4a";
    private static final String RAHA = "2df79424-556e-4215-92d5-28f51ffaff1f";
    private static final String ELIKA = "21f80899-1667-4fe3-8d69-b7e98a7d015c";
    private static final String NADER = "00f32c71-539e-4b87-871b-5dbb0f7bd59c";
    private static final String RAHELEH = "2a31c5a8-503a-4590-bb5a-fbb3107278ce";
    private static final String NINA = "9f27a118-8685-4072-8d43-85b509dcf71e";

    @Test
    void everyCompanionHasOnePackWithAKnownKind() {
        assertEquals(6, AgentPacks.all().size());
        Set<String> workflows = new HashSet<>();
        for (AgentPacks.Pack pack : AgentPacks.all()) {
            AgentType.valueOf(pack.type());
            assertTrue(workflows.add(pack.workflowId()), "one workflow per companion: " + pack.slug());
            assertFalse(pack.seed().name().isBlank());
        }
    }

    @Test
    void theAbilitiesAreTheOnesTheSettingsHad() {
        CounselingConfig config = new CounselingConfig();
        for (String companion : new String[]{POURIA, RAHA, ELIKA, NADER, RAHELEH}) {
            assertTrue(config.isCompanionWorkflow(companion));
            assertTrue(config.isOwnMethodWorkflow(companion), "the companions work by their own method");
        }
        assertFalse(config.isOwnMethodWorkflow(NINA), "the couple counselor works by Reality Therapy");
        assertTrue(config.isMarketWorkflow(POURIA));
        assertFalse(config.isMarketWorkflow(RAHA));
        assertTrue(config.isTeachWorkflow(RAHA));
        assertFalse(config.isTeachWorkflow(ELIKA));
        assertTrue(config.isKitWorkflow(ELIKA));
        assertFalse(config.isKitWorkflow(NADER));
        assertFalse(config.isCompanionWorkflow("an-unknown-bot"));
    }

    @Test
    void aMovedCompanionKeepsItsFormerWorkflow() {
        assertTrue(AgentPacks.byWorkflow(RAHA).orElseThrow().formerWorkflowIds()
                .contains("d5385904-1504-4a34-9898-a0e9d152d96a"));
    }
}
