package com.coupleai.coupleai.beinema.session;

import com.coupleai.coupleai.beinema.chat.ChatRealtimeService;
import com.coupleai.coupleai.beinema.chat.ChatRoomEventHub;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Exception.QuestionAnswerException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The room creator's first answer: alone, or with the partner. The companion runs the
 * session accordingly. The answer is asked once; a partner who joins later still joins.
 */
@Service
@RequiredArgsConstructor
public class SessionModeService {

    public static final String SOLO = "SOLO";
    public static final String COUPLE = "COUPLE";

    static final String SOLO_REPLY = "باشه، این جلسه‌ها رو با خودت جلو می‌بریم. هر وقت آماده بودی، بنویس چی شده که اومدی اینجا.";
    static final String COUPLE_REPLY = "باشه. برای اضافه کردن پارتنرت، منوی سه‌نقطه رو در گوشهٔ بالا سمت چپ همین صفحه باز کن و «افزودن پارتنر» رو بزن. "
            + "تا اون موقع هم می‌تونی خودت شروع کنی: هر وقت آماده بودی، بنویس چی شده که اومدی اینجا.";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final ChatRealtimeService chatRealtimeService;
    private final ChatRoomEventHub eventHub;
    private final SessionLifecycleService sessionLifecycleService;

    public Map<String, Object> view(Long roomId, User user) {
        return view(member(roomId, user), user);
    }

    public Map<String, Object> choose(Long roomId, String mode, User user) {
        ChatRoom room = member(roomId, user);
        if (!SOLO.equals(mode) && !COUPLE.equals(mode)) {
            throw new QuestionAnswerException("INVALID_OPTION", HttpStatus.BAD_REQUEST, "یکی از گزینه‌ها را انتخاب کن.");
        }
        if (!user.getId().equals(room.getCreatedBy())) {
            throw new QuestionAnswerException("NOT_ADDRESSEE", HttpStatus.FORBIDDEN, "این سؤال از سازندهٔ گفتگوست.");
        }
        if (room.getSessionMode() == null) {
            room.setSessionMode(mode);
            chatRoomRepository.save(room);
            Long agentId = chatRoomAgentRepository.findActiveAgentIds(roomId).stream().findFirst().orElse(0L);
            chatRealtimeService.saveAgentAndPublish(roomId, agentId, SOLO.equals(mode) ? SOLO_REPLY : COUPLE_REPLY);
            eventHub.publishNamed(roomId, "test_updated", Map.of("id", "mode-" + roomId));
            sessionLifecycleService.refresh(room);
        }
        return view(room, user);
    }

    private static Map<String, Object> view(ChatRoom room, User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mode", room.getSessionMode());
        view.put("canChoose", user.getId().equals(room.getCreatedBy()));
        return view;
    }

    private ChatRoom member(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new QuestionAnswerException("NOT_MEMBER", HttpStatus.NOT_FOUND, "این گفتگو پیدا نشد."));
        if (!participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new QuestionAnswerException("NOT_MEMBER", HttpStatus.NOT_FOUND, "این گفتگو پیدا نشد.");
        }
        return room;
    }
}
