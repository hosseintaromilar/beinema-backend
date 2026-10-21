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

    @Value("${beinema.debug.state-panel:false}")
    private boolean statePanel;

    public boolean orchestrated() {
        return mode != null && "orchestrated".equalsIgnoreCase(mode.trim());
    }

    /** One lock per room, shared by message turns, answers and platform events. */
    public static String lockKey(Long roomId) {
        return "room-" + roomId;
    }
}
