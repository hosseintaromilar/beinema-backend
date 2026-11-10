package com.coupleai.coupleai.beinema.session;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The companion of a room: which agent it is, and whether the counselor brain runs there. */
@Service
@RequiredArgsConstructor
public class CounselorSessionService {

    private final ChatRoomAgentRepository chatRoomAgentRepository;

    public Agent requireActiveAgent(ChatRoom chatRoom) {
        return chatRoomAgentRepository.findAllByChatRoom(chatRoom)
                .stream()
                .filter(link -> Boolean.TRUE.equals(link.getActive()))
                .map(link -> link.getAgent())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Agent is not attached to this chat room"));
    }

    /**
     * Every companion runs on the counselor brain (director, memory, voice), each with
     * its own Metis workflow (CounselingConfig.workflowFor); a room without an active
     * companion has no counselor.
     */
    public boolean usesNewBrain(ChatRoom chatRoom) {
        if (chatRoom == null) {
            return false;
        }
        try {
            return requireActiveAgent(chatRoom) != null;
        } catch (RuntimeException noAgent) {
            return false;
        }
    }

    /** True for the couple counselor, whose welcome describes Reality Therapy. */
    public boolean isRelationshipCounselor(ChatRoom chatRoom) {
        try {
            Agent agent = requireActiveAgent(chatRoom);
            // by type: other companions may have «زوج» in their name (همراه مالی زوج‌ها)
            String name = agent.getName() == null ? "" : agent.getName();
            return agent.getType() == AgentType.RELATIONSHIP
                    || (agent.getType() == null && name.contains("زوج"));
        } catch (RuntimeException noAgent) {
            return false;
        }
    }
}
