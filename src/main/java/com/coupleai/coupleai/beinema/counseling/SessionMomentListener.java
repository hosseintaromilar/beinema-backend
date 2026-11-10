package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.session.CounselorSessionService;
import com.coupleai.coupleai.beinema.session.SessionMoment;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The counselor speaks at the moments of the session clock: the director decides
 * it like any turn (welcome, where the work stands, and the next move).
 */
@Component
@RequiredArgsConstructor
public class SessionMomentListener {

    private final ChatRoomRepository chatRoomRepository;
    private final CounselorSessionService counselorSessionService;
    private final TurnRunner turnRunner;

    @EventListener
    public void on(SessionMoment moment) {
        ChatRoom room = chatRoomRepository.findById(moment.roomId()).orElse(null);
        if (room == null || !counselorSessionService.usesNewBrain(room)) {
            return;
        }
        turnRunner.momentAsync(room.getId(), moment.directorNote());
    }
}
