package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
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
            String raw = metisService.sendMessageAllowBlank(
                    conversationId,
                    SESSION_CREATED
                            + "\nOPENING_REQUIRED\n"
                            + "به فارسی و در دو یا سه جمله بگو این جلسه مشاوره زوج است: گوش می‌دهی، "
                            + "در زمان مناسب مداخله می‌کنی، و اگر فضا توهین‌آمیز شد ارسال پیام را موقتاً متوقف می‌کنی. "
                            + "این مکث تنبیه نیست.\n"
                            + "اصلاً از کاربر نخواه موافقت کند، قوانین را بپذیرد، یا منتظر نفر دوم بماند. "
                            + "کارت موافقت را اپلیکیشن جدا نشان می‌دهد. سؤال نپرس و دکمه نساز.\n"
                            + "هیچ کد انگلیسی و هیچ برچسب [[GOV]] در جواب ننویس."
            );
            return new GovernancePacketParser().strip(raw == null ? "" : raw);
        } catch (Exception exception) {
            log.warn("Could not send SESSION_CREATED to Metis: {}", exception.getMessage());
            return "";
        }
    }

    public static boolean isSilentWait(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        return new GovernancePacketParser().parse(content).visibleText().isBlank();
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
