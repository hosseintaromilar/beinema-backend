package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.governance.EntryGreeting;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
import com.coupleai.coupleai.beinema.governance.ReplySegments;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CounselorSessionService {

    public static final String SESSION_CREATED = "SESSION_CREATED";

    private static final Logger log = LoggerFactory.getLogger(CounselorSessionService.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final MetisService metisService;
    private final RoomTurnLock roomTurnLock;
    private final com.coupleai.coupleai.beinema.counseling.CounselingConfig counselingConfig;
    private final com.coupleai.coupleai.beinema.counseling.CounselorEventVoice counselorEventVoice;

    public Agent requireActiveAgent(ChatRoom chatRoom) {
        return chatRoomAgentRepository.findAllByChatRoom(chatRoom)
                .stream()
                .filter(link -> Boolean.TRUE.equals(link.getActive()))
                .map(link -> link.getAgent())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Agent is not attached to this chat room"));
    }

    @Transactional
    public ChatRoom ensureBound(ChatRoom chatRoom, User user) {
        Agent agent = requireActiveAgent(chatRoom);
        if (!needsNewSession(chatRoom, agent)) {
            return chatRoom;
        }

        var conversation = metisService.createConversation(agent.getAiBotId(), user);
        chatRoom.setAiConversationId(conversation.getId());
        chatRoom.setAiBotId(agent.getAiBotId());
        return chatRoomRepository.saveAndFlush(chatRoom);
    }

    public String requestOpening(String conversationId) {
        try {
            String raw = roomTurnLock.locked(conversationId, () -> metisService.sendMessageAllowBlank(
                    conversationId,
                    SESSION_CREATED + "\n" + EntryGreeting.sessionReady("")
            ));
            return new GovernancePacketParser().strip(raw == null ? "" : raw);
        } catch (Exception exception) {
            log.warn("Could not send SESSION_CREATED to Metis: {}", exception.getMessage());
            return "";
        }
    }

    /**
     * The counselor's words for a platform event (entry, consent, session start).
     * v2 (workflow): the room's Metis conversation; v3 (orchestrated): the voice
     * lane of the new workflow, with what memory knows about the couple.
     */
    public String speakEvent(ChatRoom chatRoom, User user, String cue) {
        if (usesNewBrain(chatRoom)) {
            return counselorEventVoice.speak(chatRoom, cue);
        }
        ChatRoom bound = ensureBound(chatRoom, user);
        if (bound.getAiConversationId() == null || bound.getAiConversationId().isBlank()) {
            return "";
        }
        return requestEntry(bound.getAiConversationId(), cue);
    }

    /**
     * The v3 brain (memory, clarifying questions, director and voice lanes) is the
     * couple counselor. Other agents keep their own Metis bot, exactly as before.
     */
    public boolean usesNewBrain(ChatRoom chatRoom) {
        return counselingConfig.orchestrated() && chatRoom != null && isRelationshipCounselor(chatRoom);
    }

    /** True for the couple counselor, whose welcome describes Reality Therapy. */
    public boolean isRelationshipCounselor(ChatRoom chatRoom) {
        try {
            Agent agent = requireActiveAgent(chatRoom);
            String name = agent.getName() == null ? "" : agent.getName();
            return agent.getType() == com.coupleai.coupleai.beinema.Enum.AgentType.RELATIONSHIP || name.contains("زوج");
        } catch (RuntimeException noAgent) {
            return false;
        }
    }

    public String requestEntry(String conversationId, String instruction) {
        try {
            String raw = roomTurnLock.locked(conversationId, () -> metisService.sendMessageAllowBlank(
                    conversationId,
                    instruction == null ? "" : instruction
            ));
            return new GovernancePacketParser().strip(raw == null ? "" : raw);
        } catch (Exception exception) {
            log.warn("Could not greet a participant on entry: {}", exception.getMessage());
            return "";
        }
    }

    public static boolean isSilentWait(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String visible = new GovernancePacketParser().parse(content).visibleText();
        return ReplySegments.plain(visible).isBlank();
    }

    private static boolean needsNewSession(ChatRoom chatRoom, Agent agent) {
        if (chatRoom.getAiConversationId() == null || chatRoom.getAiConversationId().isBlank()) {
            return true;
        }
        String boundBotId = chatRoom.getAiBotId();
        String currentBotId = agent.getAiBotId();
        return boundBotId == null
                || boundBotId.isBlank()
                || currentBotId == null
                || !currentBotId.equals(boundBotId);
    }
}
