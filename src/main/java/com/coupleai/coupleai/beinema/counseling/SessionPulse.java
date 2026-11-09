package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import com.coupleai.coupleai.beinema.Service.CounselorSessionService;
import com.coupleai.coupleai.beinema.governance.CounselorSignal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The companion runs the session; it does not only answer messages. While someone is in
 * an open session, this gives it a turn when the room has gone quiet, and when the two
 * have been talking for a while without it. The director then decides what to say.
 */
@Component
@RequiredArgsConstructor
public class SessionPulse {

    private static final Logger log = LoggerFactory.getLogger(SessionPulse.class);
    private static final int MAX_NUDGES = 2;
    private static final long MIN_GAP_SECONDS = 120;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomEventHub eventHub;
    private final CounselorSessionService counselorSessionService;
    private final CounselingConfig config;
    private final TurnRunner turnRunner;
    private final SessionEndCheckService endChecks;
    private final com.coupleai.coupleai.beinema.Service.SessionLifecycleService sessionLifecycleService;

    @Value("${beinema.brain.quiet-minutes:3}")
    private long quietMinutes = 3;

    @Value("${beinema.brain.listen-minutes:4}")
    private long listenMinutes = 4;

    private record Spell(int personSequence, int nudges) {
    }

    private final Map<Long, Spell> spells = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastFired = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 30000)
    public void beat() {
        if (!config.orchestrated()) {
            return;
        }
        for (ChatRoom room : chatRoomRepository.findRunning(ChatRoomStatus.ACTIVE)) {
            try {
                // the clock runs without anyone on the page: a session past its time closes and is settled
                ChatRoom current = sessionLifecycleService.refresh(room);
                if (current.getSessionStartedAt() == null || Boolean.FALSE.equals(current.getSessionActive())) {
                    continue;
                }
                pulse(current);
            } catch (RuntimeException exception) {
                log.warn("Session pulse failed in room {}: {}", room.getId(), exception.getMessage());
            }
        }
    }

    private void pulse(ChatRoom room) {
        if (room.getSessionStartedAt() == null || !counselorSessionService.usesNewBrain(room) || endChecks.hasOpen(room.getId())) {
            return;
        }
        List<ChatRoomParticipant> people = participantRepository.findAllByChatRoom(room);
        int present = (int) people.stream().filter(person -> person.getUser() != null
                && eventHub.isPresent(room.getId(), person.getUser().getId())).count();
        boolean someoneHere = present > 0;
        boolean paused = people.stream().anyMatch(person -> Boolean.TRUE.equals(person.getReentryPending())
                || (person.getPausedUntil() != null && person.getPausedUntil().isAfter(Instant.now())));
        if (!someoneHere || paused) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fired = lastFired.get(room.getId());
        if (fired != null && Duration.between(fired, now).getSeconds() < MIN_GAP_SECONDS) {
            return;
        }
        LocalDateTime lastAny = null;
        LocalDateTime lastPerson = null;
        LocalDateTime lastSpoken = null;
        int personSequence = 0;
        for (Message message : messageRepository.findByChatRoomIdAndDeletedFalseOrderBySequenceNumberAsc(room.getId())) {
            Integer start = room.getSessionStartSequence();
            int sequence = message.getSequenceNumber() == null ? 0 : message.getSequenceNumber();
            if (!MessageTexts.countsAsConversation(message) || (start != null && sequence < start)) {
                continue;
            }
            lastAny = message.getCreatedAt();
            if (message.getSenderType() == MessageSenderType.USER) {
                lastPerson = message.getCreatedAt();
                personSequence = sequence;
            } else if (!CounselorSignal.isSticker(message.getContent())) {
                lastSpoken = message.getCreatedAt();
            }
        }
        Spell spell = spells.get(room.getId());
        int nudges = spell != null && spell.personSequence() == personSequence ? spell.nudges() : 0;
        String moment = decide(now, lastAny, lastPerson, lastSpoken, nudges, quietMinutes, listenMinutes, present);
        if (moment == null) {
            return;
        }
        if (moment.startsWith("ROOM_QUIET")) {
            spells.put(room.getId(), new Spell(personSequence, nudges + 1));
        }
        lastFired.put(room.getId(), now);
        turnRunner.momentAsync(room.getId(), "[SYSTEM | SESSION] " + moment);
    }

    /**
     * The moment due now, or null. nudges: how often the quiet was already broken with no one answering.
     * present: how many of the people are on the page. When the companion spoke last, the turn is theirs:
     * one person alone is never asked again (a person thinks, writes, or steps away, and that is fine),
     * and two are asked at most once, after twice the quiet.
     */
    static String decide(LocalDateTime now, LocalDateTime lastAny, LocalDateTime lastPerson, LocalDateTime lastSpoken,
                         int nudges, long quietMinutes, long listenMinutes, int present) {
        if (lastAny == null || lastPerson == null) {
            // nobody has written yet: the welcome already said what to do, and it is not repeated
            return null;
        }
        long idle = Duration.between(lastAny, now).toMinutes();
        boolean theirTurn = lastSpoken != null && !lastPerson.isAfter(lastSpoken);
        if (theirTurn) {
            if (present <= 1 || nudges >= 1 || idle < quietMinutes * 2) {
                return null;
            }
            return "ROOM_QUIET minutes=" + idle + " nudge=1";
        }
        if (idle >= quietMinutes) {
            return nudges >= MAX_NUDGES ? null : "ROOM_QUIET minutes=" + idle + " nudge=" + (nudges + 1);
        }
        if (lastSpoken == null || !lastPerson.isAfter(lastSpoken)) {
            return null;
        }
        long unheard = Duration.between(lastSpoken, now).toMinutes();
        return unheard >= listenMinutes ? "LISTENED minutes=" + unheard : null;
    }
}
