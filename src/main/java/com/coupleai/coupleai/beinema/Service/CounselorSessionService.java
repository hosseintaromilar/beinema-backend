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
    private final SessionBriefService sessionBriefService;

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
            return rotateIfLong(chatRoom, agent, user);
        }

        var conversation = metisService.createConversation(agent.getAiBotId(), user);
        chatRoom.setAiConversationId(conversation.getId());
        chatRoom.setAiBotId(agent.getAiBotId());
        chatRoom.setAiSessionStartSequence(sessionBriefService.currentSequence(chatRoom));
        chatRoom.setAiSessionBrief(null);
        chatRoom.setAiResumeBlock(null);
        return chatRoomRepository.saveAndFlush(chatRoom);
    }

    /**
     * A long room moves to a fresh Metis session so each turn stays cheap. The
     * brief for the new session is delivered with the next message to Metis
     * (see pendingResume).
     */
    private ChatRoom rotateIfLong(ChatRoom chatRoom, Agent agent, User user) {
        if (!sessionBriefService.shouldRotate(chatRoom)) {
            return chatRoom;
        }
        try {
            var brief = sessionBriefService.buildBrief(chatRoom, user);
            var conversation = metisService.createConversation(agent.getAiBotId(), user);
            chatRoom.setAiConversationId(conversation.getId());
            chatRoom.setAiSessionStartSequence(sessionBriefService.currentSequence(chatRoom));
            chatRoom.setAiSessionBrief(brief.summary());
            chatRoom.setAiResumeBlock(brief.block());
            log.info("Room {} moved to a fresh Metis session", chatRoom.getId());
            return chatRoomRepository.saveAndFlush(chatRoom);
        } catch (Exception exception) {
            log.warn("Could not move room {} to a fresh session: {}",
                    chatRoom.getId(), exception.getMessage());
            sessionBriefService.noteFailure(chatRoom);
            return chatRoom;
        }
    }

    /** The brief waiting to be delivered to the session of this room, or empty. */
    public String pendingResume(Long chatRoomId) {
        if (chatRoomId == null) {
            return "";
        }
        return chatRoomRepository.findById(chatRoomId)
                .map(ChatRoom::getAiResumeBlock)
                .filter(block -> !block.isBlank())
                .orElse("");
    }

    /** Called once the brief has reached Metis. */
    @Transactional
    public void resumeDelivered(Long chatRoomId) {
        if (chatRoomId == null) {
            return;
        }
        chatRoomRepository.findById(chatRoomId).ifPresent(room -> {
            if (room.getAiResumeBlock() != null) {
                room.setAiResumeBlock(null);
                chatRoomRepository.save(room);
            }
        });
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
