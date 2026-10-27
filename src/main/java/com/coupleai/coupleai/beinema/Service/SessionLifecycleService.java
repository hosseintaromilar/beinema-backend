package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.SessionInfo;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.SessionParticipantSummary;
import com.coupleai.coupleai.beinema.Entity.SessionRecord;
import com.coupleai.coupleai.beinema.Entity.SessionSummary;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.SessionEndReason;
import com.coupleai.coupleai.beinema.Exception.ChatRoomAccessDeniedException;
import com.coupleai.coupleai.beinema.Exception.SessionOnBreakException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.SessionParticipantSummaryRepository;
import com.coupleai.coupleai.beinema.Repository.SessionRecordRepository;
import com.coupleai.coupleai.beinema.Repository.SessionSummaryRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.counseling.PartnerRole;
import com.coupleai.coupleai.beinema.governance.EntryGreeting;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * The clock of the counseling sessions of a room.
 *
 * First session: agreeing to the rules is entering it. When the first partner is
 * in, the platform waits 5 minutes for the other one; if the other one is already
 * here and reading the rules, it waits for their answer instead (at most 5 minutes
 * from their arrival). The 45 minutes start when both are in, or when the wait
 * ends; whoever comes later joins by agreeing to the rules.
 *
 * Later sessions: each partner enters with the start button, after a short break
 * (15 minutes) and within the limit of sessions per 24 hours. The first one opens
 * the session and may already talk with the counselor while the platform waits
 * 5 minutes for the other one.
 *
 * End: when the 45 minutes are over a break is offered. Whoever takes it rests;
 * whoever goes on may talk for up to 15 more minutes, then the session ends. When
 * everyone in the session takes the break, it ends at once. A session that ends
 * with a conversation is completed: it is summarised and counted; one in which
 * nobody wrote does not count and its number is used again.
 *
 * What the counselor says inside a session comes from the brain; the fixed lines
 * here only announce the clock. The moments a session opens or a partner enters
 * it are published as {@link SessionMoment}s, so the counselor can speak then.
 */
@Service
@RequiredArgsConstructor
public class SessionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SessionLifecycleService.class);

    /** Only the partner who did not come in time sees this, when a later session starts without them. */
    static final String LATE_NEXT_TEXT =
            "%s، ما %s دقیقه منتظرت بودیم تا بیایی، ولی نیامدی؛ برای همین جلسه را با %s شروع کردیم. "
                    + "هر وقت آمدی، «شروع» را بزن و به گفتگو بپیوند.";

    /** The same for the first session, where joining means agreeing to the session rules. */
    static final String LATE_FIRST_TEXT =
            "%s، ما %s دقیقه منتظرت بودیم تا با شروع جلسه موافقت کنی، ولی نیامدی؛ برای همین جلسه را با %s شروع کردیم. "
                    + "هر وقت آماده بودی، با قوانین جلسه موافقت کن و وارد گفتگو شو.";

    static final String PROPOSAL_TEXT =
            "وقت %s دقیقه‌ای این جلسه تموم شد. اگه موافقین، یه بریک %s دقیقه‌ای بدیم تا تازه بشین؛ "
                    + "اگه هم کسی بخواد ادامه بده، تا %s دقیقه‌ی دیگه می‌تونه با من ادامه بده.";

    static final String TOOK_BREAK_TEXT = "%s بریک را انتخاب کرد. اگر کسی بخواهد، این جلسه تا %s ادامه دارد.";

    static final String GOES_ON_TEXT = "%s ادامه می‌دهد؛ این جلسه حداکثر تا %s ادامه دارد.";

    static final String CLOSING_TEXT =
            "جلسه‌ی %s تموم شد. حرف‌های امروزمون یادم می‌مونه تا جلسه‌ی بعد از همین‌جا ادامه بدیم. "
                    + "جلسه‌ی بعد از %s می‌تونه شروع بشه؛ هر وقت آماده بودین، «شروع» رو بزنین.";

    /** The same when the session closed long after it ended (nobody was there): the break is already over. */
    static final String CLOSING_LATE_TEXT =
            "جلسه‌ی %s تموم شد. حرف‌های امروزمون یادم می‌مونه تا جلسه‌ی بعد از همین‌جا ادامه بدیم. "
                    + "هر وقت آماده بودین، «شروع» رو بزنین تا جلسه‌ی بعد شروع بشه.";

    static final String CLOSING_LIMITED_TEXT =
            "جلسه‌ی %s تموم شد. حرف‌های امروزمون یادم می‌مونه تا جلسه‌ی بعد از همین‌جا ادامه بدیم. "
                    + "برای اینکه فرصت کنین حرف‌های این جلسه‌ها رو امتحان کنین، در هر %s ساعت حداکثر %s جلسه داریم؛ جلسه‌ی بعد از %s می‌تونه شروع بشه.";

    static final String EMPTY_TEXT =
            "جلسه‌ی %s بدون گفتگو تمام شد و جلسه حساب نمی‌شود. هر وقت آماده بودید «شروع» را بزنید.";

    static final String BREAK_TEXT = "این جلسه تمام شده است. برای جلسه‌ی بعد «شروع» را بزنید.";
    static final String ENTRY_TEXT = "برای پیوستن به جلسه، اول «شروع» را بزن.";
    static final String REST_TEXT =
            "تو بریک را انتخاب کرده‌ای و تا پایان این جلسه پیامی نمی‌فرستی. اگر می‌خواهی برگردی، «برمی‌گردم» را بزن.";
    static final String LIMIT_TEXT = "در هر %s ساعت حداکثر %s جلسه برگزار می‌شود؛ جلسه‌ی بعد از %s می‌تواند شروع شود.";
    static final String CONSENT_FIRST_TEXT = "اول با قوانین جلسه موافقت کن.";
    static final String CLOSED_TEXT = "این گفتگو به‌طور کامل پایان یافته است؛ فقط می‌توانی پیام‌های قبلی را ببینی.";
    static final String LEFT_TEXT = "تو از این گفتگو خارج شده‌ای. برای نوشتن، «می‌خواهم به گروه برگردم» را بزن.";

    /** Clock times are shown in Iran time. */
    static final ZoneId DAY_ZONE = ZoneId.of("Asia/Tehran");

    private static final String[] ORDINALS = {"", "اول", "دوم", "سوم", "چهارم", "پنجم", "ششم", "هفتم", "هشتم",
            "نهم", "دهم", "یازدهم", "دوازدهم", "سیزدهم", "چهاردهم", "پانزدهم", "شانزدهم", "هفدهم", "هجدهم",
            "نوزدهم", "بیستم"};

    @Value("${beinema.session.duration-minutes:45}")
    private int defaultMinutes;

    /** How long the first partner in a session waits for the other one. */
    @Value("${beinema.session.partner-wait-minutes:5}")
    private int partnerWaitMinutes;

    /** The shortest break between two sessions. */
    @Value("${beinema.session.min-break-minutes:15}")
    private int minBreakMinutes;

    /** After the 45 minutes, whoever goes on may talk this long; then the session ends. */
    @Value("${beinema.session.overtime-minutes:15}")
    private int overtimeMinutes;

    /** The most sessions a room may hold in the window (0 = no limit). */
    @Value("${beinema.session.max-sessions:3}")
    private int maxSessions;

    @Value("${beinema.session.window-hours:24}")
    private int windowHours;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final SessionSummaryRepository summaryRepository;
    private final SessionParticipantSummaryRepository participantSummaryRepository;
    private final ChatRealtimeService chatRealtimeService;
    private final CurrentUserService currentUserService;
    private final CounselorSessionService counselorSessionService;
    private final SessionSummaryService summaryService;
    private final MessageRepository messageRepository;
    private final SessionRecordRepository sessionRecordRepository;
    private final ChatRoomEventHub eventHub;
    private final ApplicationEventPublisher events;
    private final RelationshipInsightService relationshipInsightService;
    private final SessionBillingService sessionBillingService;

    /** Tests replace these two to control time and to run the closing in place. */
    Clock clock = Clock.systemUTC();
    Executor opener = ForkJoinPool.commonPool();

    private final ConcurrentHashMap<Long, Object> roomLocks = new ConcurrentHashMap<>();

    /**
     * Two page refreshes can decide the same thing a moment apart (the room page
     * reads the session inside its own transaction). What follows from a decision
     * (summaries, the counselor's words, private notices) happens once.
     */
    private final Set<String> onceKeys = ConcurrentHashMap.newKeySet();

    // ---------------------------------------------------------------- reading

    public SessionInfo describe(ChatRoom room) {
        Instant now = clock.instant();
        long duration = durationSeconds(room);
        Instant started = room.getSessionStartedAt();
        boolean active = isActive(room);
        List<ChatRoomParticipant> people = ChatRoomParticipant.active(participantRepository.findAllByChatRoom(room));

        List<SessionInfo.Vote> votes = new ArrayList<>();
        List<SessionInfo.Ready> ready = new ArrayList<>();
        for (ChatRoomParticipant participant : people) {
            if (participant.getUser() == null) {
                continue;
            }
            votes.add(SessionInfo.Vote.builder()
                    .userId(participant.getUser().getId())
                    .name(participant.getUser().visibleName())
                    .agree(participant.getBreakVote())
                    .build());
            ready.add(SessionInfo.Ready.builder()
                    .userId(participant.getUser().getId())
                    .name(participant.getUser().visibleName())
                    .ready(participant.getSessionReadyAt() != null)
                    .build());
        }

        Long waitEndsAt = null;
        boolean forConsent = false;
        if (active && started == null && someReady(people) && waitsForPartner(room, people)) {
            Wait wait = waitFor(room, people);
            waitEndsAt = wait.until().toEpochMilli();
            forConsent = wait.forConsent();
        }
        Instant ended = room.getSessionEndedAt();
        Limit limit = active ? Limit.NONE : limit(room, now);
        int number = number(room);

        return SessionInfo.builder()
                .number(number)
                .active(active)
                .durationSeconds(duration)
                .startedAt(started == null ? null : started.toEpochMilli())
                .endsAt(started == null ? null : started.plusSeconds(duration).toEpochMilli())
                .overtimeEndsAt(started == null ? null : started.plusSeconds(duration + overtimeSeconds()).toEpochMilli())
                .serverNow(now.toEpochMilli())
                .breakProposed(active && room.getBreakProposedAt() != null)
                .votes(votes)
                .hasSummary(room.getId() != null && summaryRepository.existsByChatRoomId(room.getId()))
                .endedAt(!active && ended != null ? ended.toEpochMilli() : null)
                .nextAvailableAt(!active && ended != null ? ended.plusSeconds(minBreakMinutes * 60L).toEpochMilli() : null)
                .waitEndsAt(waitEndsAt)
                .waitingForConsent(forConsent)
                .ready(ready)
                .limitReached(limit.reached())
                .limitUntil(limit.until() == null ? null : limit.until().toEpochMilli())
                .sessionLimit(maxSessions)
                .completedSessions(Math.max(0, active ? number - 1 : number))
                .waitMinutes(partnerWaitMinutes)
                .overtimeMinutes(overtimeMinutes)
                .breakMinutes(minBreakMinutes)
                .build();
    }

    public boolean isActive(ChatRoom room) {
        return !Boolean.FALSE.equals(room.getSessionActive());
    }

    /** A session is running (answering a structured question needs no more than this). */
    public void requireActive(ChatRoom room) {
        if (!isActive(room)) {
            throw new SessionOnBreakException(SessionOnBreakException.BREAK, BREAK_TEXT);
        }
    }

    /**
     * Before a message is accepted: a session is running, this person entered it
     * and did not take the break. Agreeing to the rules is checked by the pause service.
     */
    public void requireCanWrite(ChatRoom room, User user) {
        requireOpen(room, user);
        requireActive(room);
        if (user == null) {
            return;
        }
        ChatRoomParticipant mine = participantRepository.findByChatRoomAndUser(room, user).orElse(null);
        if (mine == null || Boolean.FALSE.equals(mine.getSessionConsented())) {
            return;
        }
        if (mine.getSessionReadyAt() == null) {
            throw new SessionOnBreakException(SessionOnBreakException.ENTRY, ENTRY_TEXT);
        }
        if (Boolean.TRUE.equals(mine.getBreakVote()) && room.getBreakProposedAt() != null) {
            throw new SessionOnBreakException(SessionOnBreakException.REST, REST_TEXT);
        }
    }

    /**
     * The conversation still goes on for this person: the owner has not ended it
     * for good, and they did not leave it. Checked before writing, answering a
     * card, entering a session or voting.
     */
    public void requireOpen(ChatRoom room, User user) {
        if (room != null && room.getStatus() == ChatRoomStatus.ENDED) {
            throw new SessionOnBreakException(SessionOnBreakException.CLOSED, CLOSED_TEXT);
        }
        if (room == null || user == null) {
            return;
        }
        participantRepository.findByChatRoomAndUser(room, user)
                .filter(mine -> !mine.isActiveMember())
                .ifPresent(mine -> {
                    throw new SessionOnBreakException(SessionOnBreakException.LEFT, LEFT_TEXT);
                });
    }

    // ---------------------------------------------------------------- the clock

    /**
     * Applies what time has decided since the last look (the room page reads it
     * every few seconds; opening the room and writing read it too): starts the 45
     * minutes when the wait is over, offers the break at 45, and ends the session
     * when the 15 extra minutes are over. Safe to call often.
     */
    public ChatRoom refresh(ChatRoom room) {
        if (room == null || room.getId() == null) {
            return room;
        }
        Closing closing = null;
        Begun begun = null;
        boolean offer = false;
        ChatRoom fresh;
        synchronized (lock(room.getId())) {
            fresh = chatRoomRepository.findById(room.getId()).orElse(room);
            if (!isActive(fresh)) {
                // between sessions nothing moves by itself: each partner enters with the start button
                return fresh;
            }
            List<ChatRoomParticipant> people = ChatRoomParticipant.active(participantRepository.findAllByChatRoom(fresh));
            Instant now = clock.instant();
            enterLegacyMembers(fresh, people, now);
            Instant started = fresh.getSessionStartedAt();
            if (started == null) {
                if (!canBegin(fresh, people, now)) {
                    return fresh;
                }
                begun = begin(fresh, people, now);
            } else {
                Instant timeUp = started.plusSeconds(durationSeconds(fresh));
                Instant last = timeUp.plusSeconds(overtimeSeconds());
                if (!now.isBefore(last)) {
                    closing = close(fresh, people, last, SessionEndReason.TIME_LIMIT, anyone(people));
                } else if (!now.isBefore(timeUp) && fresh.getBreakProposedAt() == null) {
                    fresh.setBreakProposedAt(now);
                    chatRoomRepository.save(fresh);
                    offer = true;
                }
            }
        }
        if (closing != null) {
            final Closing done = closing;
            opener.execute(() -> finishClosing(done));
        } else if (offer) {
            if (once("offer:" + fresh.getId() + ":" + number(fresh) + ":" + fresh.getSessionStartedAt().toEpochMilli())) {
                say(fresh, proposalText(fresh));
            }
        } else if (begun != null && once("begin:" + fresh.getId() + ":" + number(fresh) + ":" + begun.openedAt())) {
            noticeLatecomers(fresh.getId(), begun.late(), begun.present(), number(fresh) == 1);
        }
        return fresh;
    }

    /** The phone reports that its countdown reached a limit; the server decides. */
    public SessionInfo timeUp(Long roomId) {
        ChatRoom room = requireMemberRoom(roomId);
        return describe(refresh(room));
    }

    /** Opening the room (it decides how long the first session waits for someone reading the rules). */
    public void markEntered(ChatRoom room, User user) {
        if (room == null || user == null) {
            return;
        }
        participantRepository.findByChatRoomAndUser(room, user).ifPresent(mine -> {
            mine.setEnteredAt(clock.instant());
            participantRepository.save(mine);
        });
    }

    /**
     * Agreeing to the rules is entering the session that is open now (usually the
     * first one; someone who joins later enters the running session this way).
     * Between sessions it enters nothing: the start button does. Runs inside the
     * consent's own transaction.
     */
    public void enterByConsent(ChatRoom room, User user) {
        if (room == null || room.getId() == null || user == null) {
            return;
        }
        synchronized (lock(room.getId())) {
            ChatRoom fresh = chatRoomRepository.findById(room.getId()).orElse(room);
            if (!isActive(fresh)) {
                return;
            }
            Instant now = clock.instant();
            participantRepository.findByChatRoomAndUser(fresh, user).ifPresent(mine -> {
                if (mine.getSessionReadyAt() == null) {
                    mine.setSessionReadyAt(now);
                    participantRepository.save(mine);
                }
            });
            currentRecord(fresh, now);
        }
    }

    /**
     * The start button. Between sessions it opens the next one (after the short
     * break and within the 24-hour limit); in a session that is already open it
     * brings this partner in. The counselor speaks at that moment.
     */
    public SessionInfo enterSession(Long roomId) {
        ChatRoom room = requireMemberRoom(roomId);
        User user = currentUserService.getCurrentUser();
        SessionMoment moment;
        synchronized (lock(roomId)) {
            room = chatRoomRepository.findById(roomId).orElse(room);
            Instant now = clock.instant();
            requireOpen(room, user);
            ChatRoomParticipant mine = participantRepository.findByChatRoomAndUser(room, user)
                    .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));
            if (Boolean.FALSE.equals(mine.getSessionConsented())) {
                throw new SessionOnBreakException(SessionOnBreakException.ENTRY, CONSENT_FIRST_TEXT);
            }
            List<ChatRoomParticipant> people = ChatRoomParticipant.active(participantRepository.findAllByChatRoom(room));
            if (!isActive(room)) {
                Limit limit = limit(room, now);
                if (limit.reached()) {
                    throw new SessionOnBreakException(SessionOnBreakException.LIMIT, String.format(LIMIT_TEXT,
                            persianDigits(windowHours), persianDigits(maxSessions), clockText(limit.until())));
                }
                Instant ended = room.getSessionEndedAt();
                if (ended != null && now.isBefore(ended.plusSeconds(minBreakMinutes * 60L))) {
                    long minutes = Math.max(1, (Duration.between(now, ended.plusSeconds(minBreakMinutes * 60L))
                            .getSeconds() + 59) / 60);
                    throw new SessionOnBreakException(SessionOnBreakException.BREAK,
                            "برای یک استراحت کوتاه، جلسه‌ی بعد را " + persianDigits(minutes) + " دقیقه‌ی دیگر می‌توانید شروع کنید.");
                }
                // the creator pays for sessions: a free one left, or credit for one
                sessionBillingService.requireCanStart(room, user);
                open(room, people, now);
                mine.setSessionReadyAt(now);
                participantRepository.save(mine);
                moment = opened(room, people, mine);
            } else if (mine.getSessionReadyAt() == null) {
                mine.setSessionReadyAt(now);
                participantRepository.save(mine);
                moment = entered(room, people, mine, now);
            } else {
                return describe(room);
            }
        }
        events.publishEvent(moment);
        return describe(refresh(room));
    }

    /**
     * The answer to the break offered after the 45 minutes, by someone in the
     * session. Yes: they rest until the session ends (when everyone in it says
     * yes, it ends now). No: they go on, for at most the extra minutes. An answer
     * can be changed while the extra time lasts.
     */
    public SessionInfo vote(Long roomId, boolean agree) {
        ChatRoom room = requireMemberRoom(roomId);
        User user = currentUserService.getCurrentUser();
        Closing closing = null;
        String line = null;
        synchronized (lock(roomId)) {
            room = chatRoomRepository.findById(roomId).orElse(room);
            requireOpen(room, user);
            if (!isActive(room) || room.getBreakProposedAt() == null || room.getSessionStartedAt() == null) {
                return describe(room);
            }
            ChatRoomParticipant mine = participantRepository.findByChatRoomAndUser(room, user)
                    .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));
            if (mine.getSessionReadyAt() == null || Objects.equals(mine.getBreakVote(), agree)) {
                return describe(room);
            }
            mine.setBreakVote(agree);
            participantRepository.save(mine);

            Instant now = clock.instant();
            List<ChatRoomParticipant> people = ChatRoomParticipant.active(participantRepository.findAllByChatRoom(room));
            boolean everyoneRests = people.stream()
                    .filter(person -> person.getSessionReadyAt() != null)
                    .allMatch(person -> Boolean.TRUE.equals(person.getBreakVote()));
            String until = clockText(room.getSessionStartedAt().plusSeconds(durationSeconds(room) + overtimeSeconds()));
            if (agree && everyoneRests) {
                // the summaries are written below, outside the lock: they can take a few seconds
                closing = close(room, people, now, SessionEndReason.BOTH_AGREED, user);
            } else if (agree) {
                line = String.format(TOOK_BREAK_TEXT, user.visibleName(), until);
            } else {
                line = String.format(GOES_ON_TEXT, user.visibleName(), until);
                if (room.getBreakDeclinedAt() == null) {
                    room.setBreakDeclinedAt(now);
                    chatRoomRepository.save(room);
                }
            }
        }
        if (closing != null) {
            finishClosing(closing);
        } else if (line != null) {
            chatRealtimeService.saveSystemAndPublish(roomId, line);
        }
        return describe(chatRoomRepository.findById(roomId).orElse(room));
    }

    // ---------------------------------------------------------------- beginning

    /** Older rooms keep no consent: their members are simply in the first session. */
    private void enterLegacyMembers(ChatRoom room, List<ChatRoomParticipant> people, Instant now) {
        if (number(room) != 1) {
            return;
        }
        for (ChatRoomParticipant participant : people) {
            if (participant.getSessionConsented() == null && participant.getSessionReadyAt() == null) {
                participant.setSessionReadyAt(now);
                participantRepository.save(participant);
            }
        }
    }

    /** Someone is in, and the other one came too or the wait is over. */
    private boolean canBegin(ChatRoom room, List<ChatRoomParticipant> people, Instant now) {
        if (!someReady(people)) {
            return false;
        }
        if (!waitsForPartner(room, people)) {
            return true;
        }
        if (people.size() >= 2 && allReady(people)) {
            return true;
        }
        return !now.isBefore(waitFor(room, people).until());
    }

    /** A room of one person with another counselor has nobody to wait for; a couple room always waits. */
    private boolean waitsForPartner(ChatRoom room, List<ChatRoomParticipant> people) {
        return people.size() >= 2 || counselorSessionService.isRelationshipCounselor(room);
    }

    private record Wait(Instant until, boolean forConsent) {
    }

    /**
     * The other partner has a few minutes from the moment the first one entered.
     * In the first session, if they are here reading the rules, the clock waits for
     * their answer instead (at most the same few minutes from their arrival).
     */
    private Wait waitFor(ChatRoom room, List<ChatRoomParticipant> people) {
        Instant from = people.stream()
                .map(ChatRoomParticipant::getSessionReadyAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(clock.instant());
        boolean forConsent = false;
        if (number(room) == 1) {
            for (ChatRoomParticipant person : people) {
                if (person.getSessionReadyAt() == null
                        && person.getUser() != null
                        && Boolean.FALSE.equals(person.getSessionConsented())
                        && eventHub.isPresent(room.getId(), person.getUser().getId())) {
                    forConsent = true;
                    if (person.getEnteredAt() != null && person.getEnteredAt().isAfter(from)) {
                        from = person.getEnteredAt();
                    }
                }
            }
        }
        return new Wait(from.plusSeconds(partnerWaitMinutes * 60L), forConsent);
    }

    private record Begun(Instant openedAt, List<User> late, List<User> present) {
    }

    /** The 45 minutes start. Who is not in yet hears privately that we waited and started. */
    private Begun begin(ChatRoom room, List<ChatRoomParticipant> people, Instant now) {
        room.setSessionStartedAt(now);
        chatRoomRepository.save(room);
        SessionRecord record = currentRecord(room, now);
        if (record.getStartedAt() == null) {
            record.setStartedAt(now);
            sessionRecordRepository.save(record);
        }
        return new Begun(record.getOpenedAt(), users(people, false), users(people, true));
    }

    /** Ready (or not yet ready) members, in room order. */
    private static List<User> users(List<ChatRoomParticipant> people, boolean ready) {
        return people.stream()
                .filter(item -> item.getUser() != null && (item.getSessionReadyAt() != null) == ready)
                .map(ChatRoomParticipant::getUser)
                .toList();
    }

    private void noticeLatecomers(Long roomId, List<User> late, List<User> present, boolean firstSession) {
        if (late.isEmpty() || present.isEmpty()) {
            return;
        }
        String presentNames = names(present);
        String minutes = persianDigits(partnerWaitMinutes);
        for (User user : late) {
            String text = String.format(firstSession ? LATE_FIRST_TEXT : LATE_NEXT_TEXT,
                    user.visibleName(), minutes, presentNames);
            try {
                chatRealtimeService.savePrivateSystemAndPublish(roomId, user.getId(), text);
            } catch (RuntimeException exception) {
                log.warn("Could not leave the late notice in room {}: {}", roomId, exception.getMessage());
            }
        }
    }

    /** The next session opens: a new number, nobody in yet, the clock not started. */
    private void open(ChatRoom room, List<ChatRoomParticipant> people, Instant now) {
        String summary = summaryRepository.findTopByChatRoomIdOrderBySessionNumberDesc(room.getId())
                .map(SessionSummary::getSummary)
                .orElse("");
        room.setSessionNumber(number(room) + 1);
        room.setSessionActive(true);
        room.setSessionStartedAt(null);
        room.setBreakProposedAt(null);
        room.setBreakDeclinedAt(null);
        room.setSessionStartSequence(summaryService.currentSequence(room));
        // the old workflow reads this brief with the first message; v3 carries its memory in every turn
        room.setResumeBlock(summaryService.resumeBlock(room, summary));
        room.setAiConversationId(null);
        chatRoomRepository.save(room);
        for (ChatRoomParticipant participant : people) {
            if (participant.getSessionReadyAt() != null || participant.getBreakVote() != null) {
                participant.setSessionReadyAt(null);
                participant.setBreakVote(null);
                participantRepository.save(participant);
            }
        }
        currentRecord(room, now);
    }

    /** The moment the first partner opens a session: the counselor welcomes them and says where the work stands. */
    private SessionMoment opened(ChatRoom room, List<ChatRoomParticipant> people, ChatRoomParticipant mine) {
        int number = number(room);
        List<ChatRoomParticipant> others = people.stream()
                .filter(person -> person.getUser() != null && !person.getUser().getId().equals(mine.getUser().getId()))
                .toList();
        String summary = summaryRepository.findTopByChatRoomIdOrderBySessionNumberDesc(room.getId())
                .map(SessionSummary::getSummary)
                .orElse("");
        StringBuilder note = new StringBuilder("[SYSTEM | SESSION] SESSION_OPENED session=").append(number)
                .append(" completed_sessions=").append(number - 1).append('\n')
                .append("Entered now: ").append(label(mine)).append(".\n");
        if (others.isEmpty()) {
            note.append("Not in yet: the partner has not joined the room yet. The platform waits ")
                    .append(partnerWaitMinutes).append(" minutes, then the ").append(plannedMinutes(room))
                    .append(" minutes start.\n");
        } else {
            note.append("Not in yet: ").append(others.stream().map(SessionLifecycleService::label)
                            .reduce((left, right) -> left + ", " + right).orElse(""))
                    .append(". The platform waits up to ").append(partnerWaitMinutes).append(" minutes for them; the ")
                    .append(plannedMinutes(room)).append(" minutes start when both are in, or when that wait ends.\n");
        }
        if (!summary.isBlank()) {
            note.append("LAST_SESSION_SUMMARY (private background for you; never quote it):\n").append(summary.trim())
                    .append("\nEND_OF_SUMMARY\n");
        }
        String here = mine.getUser().visibleName();
        String away = others.stream().map(person -> person.getUser().visibleName())
                .reduce((left, right) -> left + " و " + right).orElse("");
        String block = room.getResumeBlock() == null ? "" : room.getResumeBlock();
        return new SessionMoment(room.getId(), mine.getUser().getId(), note.toString(),
                block + EntryGreeting.sessionReady(here, away));
    }

    /** The moment a partner comes into a session that is already open. */
    private SessionMoment entered(ChatRoom room, List<ChatRoomParticipant> people, ChatRoomParticipant mine, Instant now) {
        StringBuilder note = new StringBuilder("[SYSTEM | SESSION] PARTNER_ENTERED_SESSION session=").append(number(room))
                .append('\n')
                .append("Entered now: ").append(label(mine)).append(".\n");
        String inside = people.stream()
                .filter(person -> person.getUser() != null && person.getSessionReadyAt() != null
                        && !person.getUser().getId().equals(mine.getUser().getId()))
                .map(SessionLifecycleService::label)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        if (!inside.isBlank()) {
            note.append("Already in: ").append(inside).append(".\n");
        }
        Instant started = room.getSessionStartedAt();
        if (started == null) {
            note.append("The ").append(plannedMinutes(room)).append(" minutes start now.\n");
        } else {
            note.append("This session has been running for ").append(Duration.between(started, now).toMinutes())
                    .append(" minutes without them.\n");
        }
        return new SessionMoment(room.getId(), mine.getUser().getId(), note.toString(), null);
    }

    /**
     * The owner ended the conversation for good (the room is already ENDED): a
     * session that is open is closed now and its summaries are written, without
     * the words about the next session.
     */
    public void closeForGood(ChatRoom room, User user) {
        if (room == null || room.getId() == null) {
            return;
        }
        Closing closing = null;
        synchronized (lock(room.getId())) {
            // a session that began and has not closed (sessionActive is null while one runs)
            if (isActive(room) && room.getSessionStartedAt() != null) {
                List<ChatRoomParticipant> people = ChatRoomParticipant.active(participantRepository.findAllByChatRoom(room));
                closing = close(room, people, clock.instant(), SessionEndReason.ENDED_FOR_GOOD, user);
            }
        }
        if (closing != null) {
            Closing done = closing;
            opener.execute(() -> finishClosing(done));
        }
    }

    /**
     * Someone just accepted the rules (their first entry): with the v3 brain the
     * counselor thanks them and asks the opening question in its own words.
     */
    public void consentMoment(ChatRoom room, ChatRoomParticipant person, boolean partnerAlreadyIn) {
        if (room == null || person == null || person.getUser() == null) {
            return;
        }
        String note = "[SYSTEM | SESSION] PARTNER_CONSENTED session=" + number(room) + "\n"
                + "First entry of " + label(person) + ": they accepted the rules before entering, and a short fixed "
                + "welcome (greeting by name and who you are) was just shown. Do not greet again, introduce yourself "
                + "or thank them for agreeing; go straight to the opening question.\n"
                + (partnerAlreadyIn
                ? "The other partner had agreed before; this person joins a conversation of two.\n"
                : "Nobody else has agreed yet.\n");
        events.publishEvent(new SessionMoment(room.getId(), person.getUser().getId(), note, null));
    }

    private static String label(ChatRoomParticipant person) {
        return PartnerRole.of(person.getRole()) + " (" + person.getUser().visibleName() + ")";
    }

    // ---------------------------------------------------------------- the end

    /** What the end of a session needs after the room is unlocked. */
    private record Closing(Long roomId, int number, int startSequence, Instant startedAt, Instant endedAt, User user,
                           boolean empty, Instant limitedUntil) {
    }

    /**
     * Ends the running session at once, inside the room lock: the clock stops,
     * nobody is in it or resting any more, and its record says how it ended.
     */
    private Closing close(ChatRoom room, List<ChatRoomParticipant> people, Instant endedAt, SessionEndReason reason, User user) {
        int number = number(room);
        boolean empty = lastUserMessageAt(room).isEmpty();
        SessionRecord record = currentRecord(room, endedAt);
        record.setEndedAt(endedAt);
        if (empty) {
            record.setEndReason(SessionEndReason.EMPTY);
            record.setCompleted(false);
        } else {
            record.setEndReason(reason);
            record.setCompleted(true);
            record.setCompletedOrdinal(number);
            if (record.getBilling() == null) {
                record.setBilling(settle(room, number));
            }
        }
        sessionRecordRepository.save(record);

        Closing closing = new Closing(room.getId(), number,
                room.getSessionStartSequence() == null ? 0 : room.getSessionStartSequence(),
                room.getSessionStartedAt(), endedAt, user, empty, null);
        if (empty) {
            // nothing happened: the number is used again and there is nothing to rest from
            room.setSessionNumber(number - 1);
            room.setSessionEndedAt(null);
        } else {
            room.setSessionEndedAt(endedAt);
        }
        room.setSessionActive(false);
        room.setBreakProposedAt(null);
        chatRoomRepository.save(room);
        for (ChatRoomParticipant participant : people) {
            if (participant.getSessionReadyAt() != null || participant.getBreakVote() != null) {
                participant.setSessionReadyAt(null);
                participant.setBreakVote(null);
                participantRepository.save(participant);
            }
        }
        if (!empty) {
            Limit limit = limit(room, clock.instant());
            Instant rested = endedAt.plusSeconds(minBreakMinutes * 60L);
            if (limit.reached() && limit.until().isAfter(rested)) {
                closing = new Closing(closing.roomId(), closing.number(), closing.startSequence(), closing.startedAt(),
                        closing.endedAt(), closing.user(), false, limit.until());
            }
        }
        return closing;
    }

    /** A session with a conversation in it is paid for when it closes; billing never stops a session closing. */
    private String settle(ChatRoom room, int number) {
        try {
            return sessionBillingService.settle(room, number);
        } catch (RuntimeException exception) {
            log.warn("Could not settle session {} of room {}: {}", number, room.getId(), exception.getMessage());
            return SessionBillingService.UNPAID;
        }
    }

    /** The private summaries of the session that just closed, then the counselor's closing words. */
    private void finishClosing(Closing closing) {
        String key = "close:" + closing.roomId() + ":" + closing.number() + ":"
                + (closing.startedAt() == null ? 0 : closing.startedAt().toEpochMilli());
        if (!once(key)) {
            return;
        }
        Long roomId = closing.roomId();
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return;
        }
        int number = closing.number();
        // a conversation ended for good gets no "next session" words, only its summaries
        boolean forGood = room.getStatus() == ChatRoomStatus.ENDED;
        if (closing.empty()) {
            if (forGood) {
                return;
            }
            chatRealtimeService.saveSystemAndPublish(roomId, String.format(EMPTY_TEXT, persianDigits(number)));
            log.info("Session {} of room {} ended without a conversation", number, roomId);
            return;
        }
        int start = closing.startSequence();
        try {
            String previous = summaryRepository.findTopByChatRoomIdOrderBySessionNumberDesc(roomId)
                    .map(SessionSummary::getSummary)
                    .orElse("");
            String summary = summaryService.summarise(room, closing.user(), start, previous);

            SessionSummary saved = new SessionSummary();
            saved.setChatRoomId(roomId);
            saved.setSessionNumber(number);
            saved.setSummary(summary == null || summary.isBlank() ? "(گفتگوی قابل‌خلاصه‌ای ثبت نشد)" : summary);
            summaryRepository.save(saved);
        } catch (RuntimeException exception) {
            log.warn("Could not save the summary of session {} of room {}: {}", number, roomId, exception.getMessage());
        }

        // what each person brought to this session, in their own words
        try {
            Map<Long, String> perPerson = summaryService.summariseParticipants(room, closing.user(), start);
            for (Map.Entry<Long, String> entry : perPerson.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()
                        || participantSummaryRepository.existsByChatRoomIdAndSessionNumberAndUserId(
                        roomId, number, entry.getKey())) {
                    continue;
                }
                SessionParticipantSummary mine = new SessionParticipantSummary();
                mine.setChatRoomId(roomId);
                mine.setSessionNumber(number);
                mine.setUserId(entry.getKey());
                mine.setSummary(entry.getValue());
                participantSummaryRepository.save(mine);
            }
        } catch (RuntimeException exception) {
            log.warn("Could not save per-person summaries of room {}: {}", roomId, exception.getMessage());
        }

        // the relationship path is written again for every session that had a conversation
        relationshipInsightService.refreshAfterSession(roomId, closing.user());

        if (!forGood) {
            say(room, closingText(closing));
        }
        log.info("Session {} of room {} closed", number, roomId);
    }

    private String closingText(Closing closing) {
        if (closing.limitedUntil() != null) {
            return String.format(CLOSING_LIMITED_TEXT, ordinal(closing.number()), persianDigits(windowHours),
                    persianDigits(maxSessions), clockText(closing.limitedUntil()));
        }
        Instant next = closing.endedAt().plusSeconds(minBreakMinutes * 60L);
        if (!next.isAfter(clock.instant())) {
            return String.format(CLOSING_LATE_TEXT, ordinal(closing.number()));
        }
        return String.format(CLOSING_TEXT, ordinal(closing.number()), clockText(next));
    }

    String proposalText(ChatRoom room) {
        return String.format(PROPOSAL_TEXT, persianDigits(plannedMinutes(room)), persianDigits(minBreakMinutes),
                persianDigits(overtimeMinutes));
    }

    private Optional<Instant> lastUserMessageAt(ChatRoom room) {
        int after = room.getSessionStartSequence() == null ? 0 : room.getSessionStartSequence();
        return messageRepository.findLastMessageAtAfter(room.getId(), MessageSenderType.USER, after)
                .map(at -> at.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static User anyone(List<ChatRoomParticipant> people) {
        return people.stream().map(ChatRoomParticipant::getUser).filter(Objects::nonNull).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- records and the limit

    /** The record of the session that is open now; made when a room has none yet (its first session, older rooms). */
    private SessionRecord currentRecord(ChatRoom room, Instant now) {
        int number = number(room);
        SessionRecord last = sessionRecordRepository.findTopByChatRoomIdOrderByIdDesc(room.getId()).orElse(null);
        if (last != null && last.getEndedAt() == null && last.getSessionNumber() != null
                && last.getSessionNumber() == number) {
            return last;
        }
        SessionRecord record = new SessionRecord();
        record.setChatRoomId(room.getId());
        record.setSessionNumber(number);
        record.setOpenedAt(room.getSessionStartedAt() != null && room.getSessionStartedAt().isBefore(now)
                ? room.getSessionStartedAt()
                : now);
        record.setStartedAt(room.getSessionStartedAt());
        SessionRecord saved = sessionRecordRepository.save(record);
        return saved == null ? record : saved;
    }

    private record Limit(boolean reached, Instant until) {
        static final Limit NONE = new Limit(false, null);
    }

    /**
     * Sessions held in the last 24 hours: the completed ones and the one running
     * now (at most one, whatever older rooms left open). At the limit: when the
     * oldest of them leaves the window.
     */
    private Limit limit(ChatRoom room, Instant now) {
        if (maxSessions <= 0 || room.getId() == null) {
            return Limit.NONE;
        }
        Duration window = Duration.ofHours(windowHours);
        List<SessionRecord> recent = sessionRecordRepository
                .findAllByChatRoomIdAndOpenedAtAfterOrderByOpenedAtAsc(room.getId(), now.minus(window));
        List<SessionRecord> held = new ArrayList<>(recent.stream()
                .filter(record -> Boolean.TRUE.equals(record.getCompleted()))
                .toList());
        recent.stream().filter(record -> record.getEndedAt() == null).reduce((first, second) -> second)
                .ifPresent(held::add);
        held.sort(Comparator.comparing(SessionRecord::getOpenedAt));
        if (held.size() < maxSessions) {
            return Limit.NONE;
        }
        return new Limit(true, held.get(held.size() - maxSessions).getOpenedAt().plus(window));
    }

    // ---------------------------------------------------------------- the previous brief and summaries

    /** The brief waiting to reach the counselor, or an empty string. */
    public String pendingResume(Long roomId) {
        if (roomId == null) {
            return "";
        }
        return chatRoomRepository.findById(roomId)
                .map(ChatRoom::getResumeBlock)
                .filter(block -> !block.isBlank())
                .orElse("");
    }

    public void resumeDelivered(Long roomId) {
        if (roomId == null) {
            return;
        }
        chatRoomRepository.findById(roomId).ifPresent(room -> {
            if (room.getResumeBlock() != null) {
                room.setResumeBlock(null);
                chatRoomRepository.save(room);
            }
        });
    }

    /** The summaries of the current user's own sessions in this room. */
    public List<SessionParticipantSummary> mySummaries(Long roomId) {
        requireMemberRoom(roomId);
        User user = currentUserService.getCurrentUser();
        return participantSummaryRepository
                .findAllByChatRoomIdAndUserIdOrderBySessionNumberAsc(roomId, user.getId());
    }

    // ---------------------------------------------------------------- helpers

    /** Planned length of a session in minutes (for the welcome text). */
    public int plannedMinutes(ChatRoom room) {
        return (int) (durationSeconds(room) / 60);
    }

    /** The shortest break between two sessions, in minutes. */
    public int breakMinutes() {
        return minBreakMinutes;
    }

    /** How long whoever goes on may talk after the 45 minutes. */
    public int overtimeMinutes() {
        return overtimeMinutes;
    }

    /** The most sessions a room may hold in 24 hours. */
    public int maxSessions() {
        return maxSessions;
    }

    /** The length of a session in a new room, in minutes. */
    public int defaultSessionMinutes() {
        return Math.max(1, defaultMinutes);
    }

    private static int number(ChatRoom room) {
        return room.getSessionNumber() == null ? 1 : room.getSessionNumber();
    }

    private long durationSeconds(ChatRoom room) {
        int minutes = room.getSessionDurationMinutes() == null || room.getSessionDurationMinutes() <= 0
                ? defaultMinutes
                : room.getSessionDurationMinutes();
        return Math.max(1, minutes) * 60L;
    }

    private long overtimeSeconds() {
        return Math.max(0, overtimeMinutes) * 60L;
    }

    private static boolean someReady(List<ChatRoomParticipant> people) {
        return people.stream().anyMatch(item -> item.getSessionReadyAt() != null);
    }

    private static boolean allReady(List<ChatRoomParticipant> people) {
        return !people.isEmpty() && people.stream().allMatch(item -> item.getSessionReadyAt() != null);
    }

    private static String names(List<User> users) {
        return users.stream()
                .map(User::visibleName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + " و " + right)
                .orElse("");
    }

    static String ordinal(int number) {
        return number > 0 && number < ORDINALS.length ? ORDINALS[number] : persianDigits(number) + "ام";
    }

    /** "ساعت ۱۴:۳۰", or "فردا ساعت ۰۹:۱۵" (Iran time). */
    String clockText(Instant moment) {
        ZonedDateTime at = moment.atZone(DAY_ZONE);
        String time = persianDigits(String.format("%02d:%02d", at.getHour(), at.getMinute()));
        LocalDate today = clock.instant().atZone(DAY_ZONE).toLocalDate();
        return at.toLocalDate().isAfter(today) ? "فردا ساعت " + time : "ساعت " + time;
    }

    static String persianDigits(long number) {
        return persianDigits(String.valueOf(number));
    }

    static String persianDigits(String latin) {
        StringBuilder out = new StringBuilder();
        for (char c : latin.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }

    private void say(ChatRoom room, String text) {
        chatRealtimeService.saveAgentAndPublish(room.getId(), agentId(room.getId()), text);
    }

    private Long agentId(Long roomId) {
        return chatRoomAgentRepository.findActiveAgentIds(roomId).stream().findFirst().orElse(0L);
    }

    private Object lock(Long roomId) {
        return roomLocks.computeIfAbsent(roomId, key -> new Object());
    }

    private boolean once(String key) {
        return onceKeys.add(key);
    }

    private ChatRoom requireMemberRoom(Long roomId) {
        User user = currentUserService.getCurrentUser();
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        if (!participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new ChatRoomAccessDeniedException("Access denied");
        }
        return room;
    }
}
