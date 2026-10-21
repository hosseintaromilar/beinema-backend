package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Service.RoomTurnLock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Runs a counselor turn in the background, in line with the room's other turns (used after an answer). */
@Service
@RequiredArgsConstructor
public class TurnRunner {

    private static final Logger log = LoggerFactory.getLogger(TurnRunner.class);

    private final TurnOrchestrator orchestrator;
    private final RoomTurnLock roomTurnLock;
    private final CounselingConfig config;
    private final TurnInbox inbox;

    /**
     * A moment of the session (it opened, a partner entered): the note waits in
     * the inbox and the director decides what the counselor says, as in any turn.
     */
    public void momentAsync(Long roomId, String note) {
        if (!config.orchestrated() || roomId == null || note == null || note.isBlank()) {
            return;
        }
        inbox.add(roomId, null, note);
        runAsync(roomId);
    }

    public void runAsync(Long roomId) {
        if (!config.orchestrated() || roomId == null) {
            return;
        }
        Thread thread = new Thread(() -> {
            String key = CounselingConfig.lockKey(roomId);
            roomTurnLock.lock(key);
            try {
                TurnOrchestrator.Outcome outcome = orchestrator.runTurn(roomId);
                orchestrator.deliverRemaining(outcome);
            } catch (Exception exception) {
                log.warn("Background counselor turn failed in room {}", roomId, exception);
            } finally {
                roomTurnLock.unlock(key);
            }
        }, "beinema-turn-" + roomId);
        thread.setDaemon(true);
        thread.start();
    }
}
