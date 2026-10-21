package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Service.MetisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Calls the v3 workflow. Every call runs in a new Metis session, so nothing
 * piles up in Metis history and each call costs only what it sends: the memory
 * lives in the backend and travels in the packet. The packet's first line
 * (##BEINEMA_MODE=DIRECTOR## or ##BEINEMA_MODE=VOICE##) picks the lane.
 */
@Service
@RequiredArgsConstructor
public class MetisWorkflowGateway implements BrainGateway {

    private static final String CALLER_KEY = "beinema-counselor";
    private static final String CALLER_NAME = "Beinema counselor";

    private final MetisService metisService;
    private final CounselingConfig config;

    @Override
    public Reply director(String packet) {
        return call(packet);
    }

    @Override
    public Reply voice(String packet) {
        return call(packet);
    }

    @Override
    public Reply planner(String packet) {
        return call(packet);
    }

    private Reply call(String packet) {
        String workflowId = config.getWorkflowId();
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalStateException("beinema.brain.workflow-id is not set");
        }
        long started = System.currentTimeMillis();
        var session = metisService.createConversation(workflowId, CALLER_KEY, CALLER_NAME);
        String reply = metisService.sendMessageAllowBlank(session.getId(), packet);
        return new Reply(reply == null ? "" : reply, System.currentTimeMillis() - started);
    }
}
