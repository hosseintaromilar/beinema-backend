package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Service.MetisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Calls the v3 workflow. Every call runs in a new Metis session, so nothing
 * piles up in Metis history and each call costs only what it sends: the memory
 * lives in the backend and travels in the packet. The packet's first line
 * (##BEINEMA_MODE=DIRECTOR##, VOICE, PLANNER or TEACHER) picks the lane.
 * forRoom picks the workflow: the room companion's own copy of v3 when it has
 * one (CounselingConfig.companionWorkflowIds), else the couple counselor's.
 */
@Service
@RequiredArgsConstructor
public class MetisWorkflowGateway implements BrainGateway {

    private static final String CALLER_KEY = "beinema-counselor";
    private static final String CALLER_NAME = "Beinema counselor";

    private final MetisService metisService;
    private final CounselingConfig config;
    private final ChatRoomAgentRepository chatRoomAgentRepository;

    @Override
    public Reply director(String packet) {
        return call(config.getWorkflowId(), packet);
    }

    @Override
    public Reply voice(String packet) {
        return call(config.getWorkflowId(), packet);
    }

    @Override
    public Reply planner(String packet) {
        return call(config.getWorkflowId(), packet);
    }

    @Override
    public Reply teacher(String packet) {
        return call(config.getWorkflowId(), packet);
    }

    @Override
    public BrainGateway forRoom(ChatRoom room) {
        String agentBotId = room == null ? null : chatRoomAgentRepository.findActiveAgentBotIds(room.getId()).stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        String workflowId = config.workflowFor(agentBotId);
        return new BrainGateway() {
            @Override
            public Reply director(String packet) {
                return call(workflowId, packet);
            }

            @Override
            public Reply voice(String packet) {
                return call(workflowId, packet);
            }

            @Override
            public Reply planner(String packet) {
                return call(workflowId, packet);
            }

            @Override
            public Reply teacher(String packet) {
                return call(workflowId, packet);
            }
        };
    }

    private Reply call(String workflowId, String packet) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalStateException("beinema.brain.workflow-id is not set");
        }
        long started = System.currentTimeMillis();
        var session = metisService.createConversation(workflowId, CALLER_KEY, CALLER_NAME);
        String reply = metisService.sendMessageAllowBlank(session.getId(), packet);
        return new Reply(reply == null ? "" : reply, System.currentTimeMillis() - started);
    }
}
