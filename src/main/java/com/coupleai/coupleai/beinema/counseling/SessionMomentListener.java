package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.coupleai.coupleai.beinema.Service.CounselorSessionService;
import com.coupleai.coupleai.beinema.Service.SessionLifecycleService;
import com.coupleai.coupleai.beinema.Service.SessionMoment;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The counselor speaks at the moments of the session clock. With the v3 brain
 * the director decides it like any turn (welcome, where the work stands, and the
 * next move); the old workflow still gets its fixed cue.
 */
@Component
@RequiredArgsConstructor
public class SessionMomentListener {

    private static final Logger log = LoggerFactory.getLogger(SessionMomentListener.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final UserRepository userRepository;
    private final CounselorSessionService counselorSessionService;
    private final ChatRealtimeService chatRealtimeService;
    private final SessionLifecycleService sessionLifecycleService;
    private final TurnRunner turnRunner;

    @EventListener
    public void on(SessionMoment moment) {
        ChatRoom room = chatRoomRepository.findById(moment.roomId()).orElse(null);
        if (room == null) {
            return;
        }
        if (counselorSessionService.usesNewBrain(room)) {
            turnRunner.momentAsync(room.getId(), moment.directorNote());
            return;
        }
        if (moment.v2Cue() == null || moment.v2Cue().isBlank()) {
            return;
        }
        Thread thread = new Thread(() -> speakTheOldWay(room, moment), "beinema-session-" + room.getId());
        thread.setDaemon(true);
        thread.start();
    }

    private void speakTheOldWay(ChatRoom room, SessionMoment moment) {
        try {
            User user = moment.userId() == null ? null : userRepository.findById(moment.userId()).orElse(null);
            String spoken = counselorSessionService.speakEvent(room, user, moment.v2Cue());
            if (spoken != null && !CounselorSessionService.isSilentWait(spoken)) {
                Long agentId = chatRoomAgentRepository.findActiveAgentIds(room.getId()).stream().findFirst().orElse(0L);
                chatRealtimeService.saveAgentAndPublish(room.getId(), agentId, spoken);
                sessionLifecycleService.resumeDelivered(room.getId());
            }
        } catch (RuntimeException exception) {
            log.warn("Could not speak at the session moment of room {}: {}", room.getId(), exception.getMessage());
        }
    }
}
