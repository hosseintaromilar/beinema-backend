package com.coupleai.coupleai.beinema.counseling;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Settings of the counselor brain. beinema.brain.mode=workflow keeps the v2
 * Metis workflow exactly as before; orchestrated runs the v3 pipeline (director
 * and voice lanes of beinema.brain.workflow-id, with memory, questions and
 * validation in the backend).
 */
@Component
@Getter
@Setter
public class CounselingConfig {

    @Value("${beinema.brain.mode:workflow}")
    private String mode = "workflow";

    @Value("${beinema.brain.workflow-id:}")
    private String workflowId = "";

    /**
     * The v3 workflows of the other companions (finance, parenting...), comma-separated
     * Metis ids. Each is its own copy of the v3 workflow with its own domain; a room whose
     * companion uses one of them runs on it, every other room on workflow-id.
     */
    @Value("${beinema.brain.companion-workflow-ids:}")
    private String companionWorkflowIds = "";

    /**
     * The workflows among them that work by their own method, not by Reality Therapy: they
     * get no choice-theory rules or lessons in the packet, only the safety cards.
     */
    @Value("${beinema.brain.own-method-workflow-ids:}")
    private String ownMethodWorkflowIds = "";

    @Value("${beinema.brain.recent-messages:16}")
    private int recentMessages = 16;

    @Value("${beinema.brain.voice-recent-messages:6}")
    private int voiceRecentMessages = 6;

    @Value("${beinema.brain.max-rule-cards:8}")
    private int maxRuleCards = 8;

    @Value("${beinema.brain.max-rule-chars:3200}")
    private int maxRuleChars = 3200;

    /** Every reading is checked with a card; this only stops a runaway loop. */
    @Value("${beinema.brain.questions-per-session:10}")
    private int questionsPerSession = 10;

    /** Phone numbers that may open the counselor's decision path in a room (comma-separated). */
    @Value("${beinema.debug.state-panel-phones:}")
    private String statePanelPhones = "";

    public boolean orchestrated() {
        return mode != null && "orchestrated".equalsIgnoreCase(mode.trim());
    }

    /** True when this Metis id is one of the companions' own v3 workflows. */
    public boolean isCompanionWorkflow(String botId) {
        return listed(companionWorkflowIds, botId);
    }

    /** True when this companion's workflow has its own method (see ownMethodWorkflowIds). */
    public boolean isOwnMethodWorkflow(String botId) {
        return listed(ownMethodWorkflowIds, botId);
    }

    private static boolean listed(String list, String botId) {
        if (botId == null || botId.isBlank() || list == null) {
            return false;
        }
        for (String id : list.split(",")) {
            if (id.trim().equals(botId.trim())) {
                return true;
            }
        }
        return false;
    }

    /** The workflow a room's companion runs on: its own when it has one, else the couple counselor's. */
    public String workflowFor(String agentBotId) {
        return isCompanionWorkflow(agentBotId) ? agentBotId.trim() : workflowId;
    }

    /** One lock per room, shared by message turns, answers and platform events. */
    public static String lockKey(Long roomId) {
        return "room-" + roomId;
    }
}
