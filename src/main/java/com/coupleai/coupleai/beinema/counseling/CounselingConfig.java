package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.agents.AgentPacks;
import com.coupleai.coupleai.beinema.agents.AgentPacks.Ability;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Settings of the counselor brain: the director and voice lanes of each companion's
 * own Metis workflow (beinema.brain.workflow-id for the couple counselor), with
 * memory, questions and validation in the backend.
 */
@Component
@Getter
@Setter
public class CounselingConfig {

    @Value("${beinema.brain.workflow-id:}")
    private String workflowId = "";

    /*
     * Which companion runs on which workflow and what it can do comes from its pack
     * (classpath:agents/<slug>.json, AgentPacks). These comma-separated lists only add to
     * the packs, for a workflow being tried before it gets a pack of its own.
     */
    @Value("${beinema.brain.companion-workflow-ids:}")
    private String companionWorkflowIds = "";

    @Value("${beinema.brain.own-method-workflow-ids:}")
    private String ownMethodWorkflowIds = "";

    @Value("${beinema.brain.kit-workflow-ids:}")
    private String kitWorkflowIds = "";

    @Value("${beinema.brain.market-workflow-ids:}")
    private String marketWorkflowIds = "";

    @Value("${beinema.brain.teach-workflow-ids:}")
    private String teachWorkflowIds = "";

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

    /** True when this Metis id is a companion's own workflow. */
    public boolean isCompanionWorkflow(String botId) {
        return AgentPacks.byWorkflow(botId).isPresent() || listed(companionWorkflowIds, botId);
    }

    /** True when this companion works by its own method: no choice-theory rules or lessons, only the safety cards. */
    public boolean isOwnMethodWorkflow(String botId) {
        return has(botId, Ability.OWN_METHOD) || listed(ownMethodWorkflowIds, botId);
    }

    /** True when this companion works with the kit of kb/elika-kit.json: tests and visual lessons. */
    public boolean isKitWorkflow(String botId) {
        return has(botId, Ability.KIT) || listed(kitWorkflowIds, botId);
    }

    /** True when this companion teaches with lesson cards (TeachKit). */
    public boolean isTeachWorkflow(String botId) {
        return has(botId, Ability.TEACH) || listed(teachWorkflowIds, botId);
    }

    /** True when this companion gets today's market prices in its packet ([MARKET]). */
    public boolean isMarketWorkflow(String botId) {
        return has(botId, Ability.MARKET) || listed(marketWorkflowIds, botId);
    }

    private static boolean has(String botId, Ability ability) {
        return AgentPacks.byWorkflow(botId).map(pack -> pack.can(ability)).orElse(false);
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
