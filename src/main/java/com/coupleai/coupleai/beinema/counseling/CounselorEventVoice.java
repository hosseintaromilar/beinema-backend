package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Enum.DecisionKind;
import com.coupleai.coupleai.beinema.Enum.DecisionStatus;
import com.coupleai.coupleai.beinema.Repository.DirectorDecisionRecordRepository;
import com.coupleai.coupleai.beinema.Service.RoomTurnLock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Platform events (a partner enters, consents, the session starts or continues
 * after a break) are spoken by the voice lane directly, with a fixed cue and
 * what memory knows about where the couple are. No director call, as in v2.
 */
@Service
@RequiredArgsConstructor
public class CounselorEventVoice {

    private static final Logger log = LoggerFactory.getLogger(CounselorEventVoice.class);

    private final TurnMaterialLoader loader;
    private final BrainGateway brain;
    private final RoomTurnLock roomTurnLock;
    private final CounselingConfig config;
    private final DirectorDecisionRecordRepository decisionRepository;

    /** The counselor's words for the event, or "" when nothing could be written. */
    public String speak(ChatRoom room, String cue) {
        if (room == null) {
            return "";
        }
        String key = CounselingConfig.lockKey(room.getId());
        roomTurnLock.lock(key);
        DirectorDecisionRecord record = new DirectorDecisionRecord();
        record.setChatRoomId(room.getId());
        record.setKind(DecisionKind.EVENT);
        record.setAction("EVENT");
        try {
            TurnMaterial m = loader.load(room.getId());
            record.setSessionNumber(m.sessionNumber());
            String packet = VoicePacketBuilder.forEvent(m, cue, config.getVoiceRecentMessages());
            record.setPacketChars(packet.length());
            record.setRawOutput(TurnPersistence.clip(cue, 4000));
            BrainGateway.Reply reply = brain.forRoom(room).voice(packet);
            record.setVoiceMs(reply.millis());
            record.setVoiceOutput(TurnPersistence.clip(reply.text(), 20000));
            VoiceOutputParser.Result out = VoiceOutputParser.parse(reply.text());
            record.setStatus(out.failed() ? DecisionStatus.FAILED : DecisionStatus.DONE);
            record.setError(out.error());
            return out.failed() ? "" : out.text();
        } catch (RuntimeException exception) {
            log.warn("Event voice failed in room {}: {}", room.getId(), exception.getMessage());
            record.setStatus(DecisionStatus.FAILED);
            record.setError(TurnPersistence.clip(exception.getMessage(), 1000));
            return "";
        } finally {
            try {
                decisionRepository.save(record);
            } catch (RuntimeException ignored) {
                // the trace must not break the welcome
            }
            roomTurnLock.unlock(key);
        }
    }
}
