package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.SessionInfo;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.SessionParticipantSummary;
import com.coupleai.coupleai.beinema.Entity.SessionRecord;
import com.coupleai.coupleai.beinema.Entity.SessionSummary;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Enum.SessionEndReason;
import com.coupleai.coupleai.beinema.Exception.SessionOnBreakException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.SessionParticipantSummaryRepository;
import com.coupleai.coupleai.beinema.Repository.SessionRecordRepository;
import com.coupleai.coupleai.beinema.Repository.SessionSummaryRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The session clock as the product describes it: the first session is entered by
 * agreeing to the rules, later ones with the start button; the first one in waits
 * five minutes for the other; 45 minutes, then a break or up to 15 extra minutes;
 * a short break between sessions and at most three sessions in 24 hours.
 */
class SessionLifecycleServiceTest {

    /** 13:30 in Tehran. */
    private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");
    private static final long MIN = 60;

    private ChatRoomRepository rooms;
    private ChatRoomParticipantRepository participants;
    private SessionSummaryRepository summaries;
    private SessionParticipantSummaryRepository personSummaries;
    private ChatRealtimeService realtime;
    private CurrentUserService currentUser;
    private CounselorSessionService counselor;
    private SessionSummaryService summaryService;
    private MessageRepository messages;
    private SessionRecordRepository sessionRecords;
    private ChatRoomEventHub eventHub;
    private ApplicationEventPublisher events;
    private SessionLifecycleService service;

    private final List<SessionRecord> records = new ArrayList<>();
    private ChatRoom room;
    private User afsaneh;
    private User hossein;
    private ChatRoomParticipant afsanehLink;
    private ChatRoomParticipant hosseinLink;
    private Instant now = T0;

    @BeforeEach
    void setUp() {
        rooms = mock(ChatRoomRepository.class);
        participants = mock(ChatRoomParticipantRepository.class);
        ChatRoomAgentRepository agents = mock(ChatRoomAgentRepository.class);
        summaries = mock(SessionSummaryRepository.class);
        personSummaries = mock(SessionParticipantSummaryRepository.class);
        realtime = mock(ChatRealtimeService.class);
        currentUser = mock(CurrentUserService.class);
        counselor = mock(CounselorSessionService.class);
        summaryService = mock(SessionSummaryService.class);
        messages = mock(MessageRepository.class);
        sessionRecords = mock(SessionRecordRepository.class);
        eventHub = mock(ChatRoomEventHub.class);
        events = mock(ApplicationEventPublisher.class);
        service = new SessionLifecycleService(
                rooms, participants, agents, summaries, personSummaries,
                realtime, currentUser, counselor, summaryService, messages, sessionRecords, eventHub, events,
                mock(RelationshipInsightService.class), mock(SessionBillingService.class));
        ReflectionTestUtils.setField(service, "defaultMinutes", 45);
        ReflectionTestUtils.setField(service, "partnerWaitMinutes", 5);
        ReflectionTestUtils.setField(service, "minBreakMinutes", 15);
        ReflectionTestUtils.setField(service, "overtimeMinutes", 15);
        ReflectionTestUtils.setField(service, "maxSessions", 3);
        ReflectionTestUtils.setField(service, "windowHours", 24);
        service.opener = Runnable::run;
        service.clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };

        room = new ChatRoom();
        room.setId(5L);
        afsaneh = user(1L, "افسانه");
        hossein = user(3L, "حسین");
        afsanehLink = link(afsaneh, ParticipantRole.OWNER);
        hosseinLink = link(hossein, ParticipantRole.PARTNER_B);

        when(rooms.findById(5L)).thenReturn(Optional.of(room));
        when(rooms.save(any(ChatRoom.class))).thenAnswer(call -> call.getArgument(0));
        when(participants.findAllByChatRoom(room)).thenReturn(List.of(afsanehLink, hosseinLink));
        when(participants.existsByChatRoomAndUser(any(), any())).thenReturn(true);
        when(participants.findByChatRoomAndUser(room, afsaneh)).thenReturn(Optional.of(afsanehLink));
        when(participants.findByChatRoomAndUser(room, hossein)).thenReturn(Optional.of(hosseinLink));
        when(agents.findActiveAgentIds(5L)).thenReturn(List.of(9L));
        when(currentUser.getCurrentUser()).thenReturn(afsaneh);
        when(summaries.findTopByChatRoomIdOrderBySessionNumberDesc(5L)).thenReturn(Optional.empty());
        when(summaryService.currentSequence(room)).thenReturn(42);
        when(summaryService.resumeBlock(eq(room), anyString())).thenReturn("[BLOCK]");
        when(summaryService.summarise(any(), any(), anyInt(), anyString())).thenReturn("خلاصه‌ی جلسه");

        // the session records, kept in memory
        when(sessionRecords.save(any(SessionRecord.class))).thenAnswer(call -> {
            SessionRecord record = call.getArgument(0);
            if (records.stream().noneMatch(saved -> saved == record)) {
                records.add(record);
            }
            return record;
        });
        when(sessionRecords.findTopByChatRoomIdOrderByIdDesc(5L)).thenAnswer(call ->
                records.isEmpty() ? Optional.empty() : Optional.of(records.get(records.size() - 1)));
        when(sessionRecords.countByChatRoomIdAndCompletedTrue(5L)).thenAnswer(call ->
                records.stream().filter(record -> Boolean.TRUE.equals(record.getCompleted())).count());
        when(sessionRecords.findAllByChatRoomIdAndOpenedAtAfterOrderByOpenedAtAsc(eq(5L), any())).thenAnswer(call -> {
            Instant after = call.getArgument(1);
            return records.stream()
                    .filter(record -> record.getOpenedAt() != null && record.getOpenedAt().isAfter(after))
                    .sorted(Comparator.comparing(SessionRecord::getOpenedAt))
                    .toList();
        });
    }

    private static User user(long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }

    private static ChatRoomParticipant link(User user, ParticipantRole role) {
        ChatRoomParticipant participant = new ChatRoomParticipant();
        participant.setUser(user);
        participant.setRole(role);
        participant.setCreatedAt(LocalDateTime.ofInstant(T0.minusSeconds(3600), ZoneId.systemDefault()));
        participant.setSessionConsented(false);
        return participant;
    }

    /** What the consent card does: agreeing to the rules enters the open session. */
    private void consent(ChatRoomParticipant participant) {
        participant.setSessionConsented(true);
        service.enterByConsent(room, participant.getUser());
    }

    private void someoneWrote(Instant at) {
        when(messages.findLastMessageAtAfter(eq(5L), eq(MessageSenderType.USER), anyInt()))
                .thenReturn(Optional.of(LocalDateTime.ofInstant(at, ZoneId.systemDefault())));
    }

    /** Both are in the first session and its 45 minutes run from T0. */
    private void bothInTheFirstSession() {
        consent(afsanehLink);
        consent(hosseinLink);
        service.refresh(room);
        assertEquals(T0, room.getSessionStartedAt());
    }

    private void as(User user) {
        when(currentUser.getCurrentUser()).thenReturn(user);
    }

    private SessionMoment lastMoment() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return (SessionMoment) captor.getValue();
    }

    // ------------------------------------------------------------ the first session

    @Test
    void nothingStartsBeforeAnyoneAgrees() {
        service.refresh(room);

        assertNull(room.getSessionStartedAt());
        SessionInfo info = service.describe(room);
        assertNull(info.getWaitEndsAt());
        assertEquals(45 * MIN, info.getDurationSeconds());
        assertEquals(0, info.getCompletedSessions());
    }

    @Test
    void theFirstToAgreeStartsAFiveMinuteWaitForTheOther() {
        consent(afsanehLink);
        service.refresh(room);

        assertNull(room.getSessionStartedAt(), "the 45 minutes do not start yet");
        SessionInfo info = service.describe(room);
        assertEquals(T0.plusSeconds(5 * MIN).toEpochMilli(), info.getWaitEndsAt());
        assertFalse(info.getWaitingForConsent());
        assertEquals(1, records.size(), "the first session is recorded from the first consent");
    }

    @Test
    void theFortyFiveMinutesStartWhenTheSecondAgrees() {
        consent(afsanehLink);
        now = T0.plusSeconds(2 * MIN);
        consent(hosseinLink);

        service.refresh(room);

        assertEquals(now, room.getSessionStartedAt());
        assertEquals(now.plusSeconds(45 * MIN).toEpochMilli(), service.describe(room).getEndsAt());
        assertEquals(now, records.get(0).getStartedAt());
        verify(realtime, never()).savePrivateSystemAndPublish(anyLong(), anyLong(), anyString());
    }

    @Test
    void theSessionStartsWithTheFirstWhenTheWaitRunsOut() {
        consent(afsanehLink);
        now = T0.plusSeconds(5 * MIN - 1);
        service.refresh(room);
        assertNull(room.getSessionStartedAt());

        now = T0.plusSeconds(5 * MIN + 1);
        service.refresh(room);

        assertEquals(now, room.getSessionStartedAt());
        // only Hossein, who did not come in time, is told that we waited and started
        verify(realtime).savePrivateSystemAndPublish(eq(5L), eq(3L), contains("منتظرت بودیم"));
        verify(realtime, never()).savePrivateSystemAndPublish(eq(5L), eq(1L), anyString());
    }

    @Test
    void aPartnerWhoIsHereReadingTheRulesHoldsTheClockAtFortyFive() {
        consent(afsanehLink);
        now = T0.plusSeconds(3 * MIN);
        hosseinLink.setEnteredAt(now);
        when(eventHub.isPresent(5L, 3L)).thenReturn(true);

        now = T0.plusSeconds(6 * MIN);
        service.refresh(room);

        assertNull(room.getSessionStartedAt(), "he is reading the rules; the 45 minutes wait for his answer");
        assertTrue(service.describe(room).getWaitingForConsent());

        consent(hosseinLink);
        service.refresh(room);
        assertEquals(now, room.getSessionStartedAt(), "his consent starts the 45 minutes");
    }

    @Test
    void someoneReadingTheRulesForTooLongDoesNotHoldTheSessionForever() {
        consent(afsanehLink);
        hosseinLink.setEnteredAt(T0.plusSeconds(3 * MIN));
        when(eventHub.isPresent(5L, 3L)).thenReturn(true);

        now = T0.plusSeconds(8 * MIN + 1);
        service.refresh(room);

        assertEquals(now, room.getSessionStartedAt(), "at most five minutes from his arrival");
    }

    @Test
    void aCoupleRoomWithoutThePartnerYetWaitsFiveMinutesThenStarts() {
        when(participants.findAllByChatRoom(room)).thenReturn(List.of(afsanehLink));
        when(counselor.isRelationshipCounselor(room)).thenReturn(true);
        consent(afsanehLink);

        now = T0.plusSeconds(4 * MIN);
        service.refresh(room);
        assertNull(room.getSessionStartedAt());

        now = T0.plusSeconds(5 * MIN);
        service.refresh(room);
        assertEquals(now, room.getSessionStartedAt());
    }

    @Test
    void aOnePersonRoomOfAnotherCounselorStartsAtOnce() {
        when(participants.findAllByChatRoom(room)).thenReturn(List.of(afsanehLink));
        consent(afsanehLink);

        service.refresh(room);

        assertEquals(T0, room.getSessionStartedAt());
    }

    @Test
    void olderRoomsWithoutConsentRecordsStartForEveryone() {
        afsanehLink.setSessionConsented(null);
        hosseinLink.setSessionConsented(null);

        service.refresh(room);

        assertEquals(T0, room.getSessionStartedAt());
    }

    @Test
    void aPartnerWhoAgreesLaterJoinsTheRunningSession() {
        consent(afsanehLink);
        now = T0.plusSeconds(6 * MIN);
        service.refresh(room);
        Instant started = room.getSessionStartedAt();

        now = T0.plusSeconds(20 * MIN);
        consent(hosseinLink);
        service.refresh(room);

        assertEquals(started, room.getSessionStartedAt(), "the clock does not start again");
        assertNotNull(hosseinLink.getSessionReadyAt());
        service.requireCanWrite(room, hossein);
    }

    // ------------------------------------------------------------ the end of a session

    @Test
    void nothingHappensBeforeTheFortyFiveMinutesAreOver() {
        bothInTheFirstSession();
        now = T0.plusSeconds(44 * MIN);

        service.refresh(room);

        assertNull(room.getBreakProposedAt());
        verify(realtime, never()).saveAgentAndPublish(anyLong(), anyLong(), anyString());
    }

    @Test
    void theBreakIsOfferedOnceWhenTheFortyFiveMinutesAreOver() {
        bothInTheFirstSession();
        now = T0.plusSeconds(45 * MIN + 1);

        service.refresh(room);
        service.timeUp(5L);
        service.refresh(room);

        verify(realtime, times(1)).saveAgentAndPublish(eq(5L), eq(9L), contains("بریک"));
        SessionInfo info = service.describe(room);
        assertTrue(info.getBreakProposed());
        assertEquals(T0.plusSeconds(60 * MIN).toEpochMilli(), info.getOvertimeEndsAt());
    }

    @Test
    void whoTakesTheBreakRestsAndTheOtherGoesOnForAtMostFifteenMinutes() {
        bothInTheFirstSession();
        someoneWrote(T0.plusSeconds(30 * MIN));
        now = T0.plusSeconds(45 * MIN + 1);
        service.refresh(room);

        as(afsaneh);
        service.vote(5L, true);
        as(hossein);
        service.vote(5L, false);

        assertTrue(service.isActive(room));
        verify(realtime).saveSystemAndPublish(eq(5L), contains("افسانه بریک را انتخاب کرد"));
        verify(realtime).saveSystemAndPublish(eq(5L), contains("حسین ادامه می‌دهد"));
        SessionOnBreakException resting = assertThrows(SessionOnBreakException.class,
                () -> service.requireCanWrite(room, afsaneh));
        assertEquals(SessionOnBreakException.REST, resting.getCode());
        service.requireCanWrite(room, hossein);

        now = T0.plusSeconds(60 * MIN + 30);
        service.refresh(room);

        assertFalse(room.getSessionActive(), "the extra fifteen minutes are over");
        assertEquals(T0.plusSeconds(60 * MIN), room.getSessionEndedAt());
        assertEquals(SessionEndReason.TIME_LIMIT, records.get(0).getEndReason());
        verify(summaries).save(any(SessionSummary.class));
        verify(realtime).saveAgentAndPublish(eq(5L), eq(9L), contains("جلسه‌ی اول تموم شد. حرف‌های امروزمون"));
    }

    @Test
    void whenEveryoneInTheSessionTakesTheBreakItEndsAtOnce() {
        bothInTheFirstSession();
        room.setSessionStartSequence(10);
        someoneWrote(T0.plusSeconds(40 * MIN));
        when(summaryService.summarise(eq(room), any(), eq(10), eq(""))).thenReturn("خلاصه‌ی جلسه");
        when(summaryService.summariseParticipants(eq(room), any(), eq(10)))
                .thenReturn(Map.of(1L, "افسانه تنهایی می‌کند", 3L, "حسین خسته است"));
        now = T0.plusSeconds(46 * MIN);
        service.refresh(room);

        as(afsaneh);
        service.vote(5L, true);
        as(hossein);
        service.vote(5L, true);

        assertFalse(room.getSessionActive());
        assertEquals(now, room.getSessionEndedAt());
        assertNull(afsanehLink.getSessionReadyAt(), "nobody is in a session between sessions");
        assertNull(hosseinLink.getBreakVote());
        SessionRecord record = records.get(0);
        assertEquals(SessionEndReason.BOTH_AGREED, record.getEndReason());
        assertTrue(record.getCompleted());
        assertEquals(1, record.getCompletedOrdinal());

        ArgumentCaptor<SessionSummary> saved = ArgumentCaptor.forClass(SessionSummary.class);
        verify(summaries).save(saved.capture());
        assertEquals("خلاصه‌ی جلسه", saved.getValue().getSummary());
        ArgumentCaptor<SessionParticipantSummary> mine = ArgumentCaptor.forClass(SessionParticipantSummary.class);
        verify(personSummaries, times(2)).save(mine.capture());
        verify(realtime).saveAgentAndPublish(eq(5L), eq(9L), contains("جلسه‌ی اول تموم شد. حرف‌های امروزمون"));

        SessionInfo info = service.describe(room);
        assertEquals(1, info.getCompletedSessions());
        assertEquals(now.plusSeconds(15 * MIN).toEpochMilli(), info.getNextAvailableAt());
        assertThrows(SessionOnBreakException.class, () -> service.requireCanWrite(room, afsaneh));
    }

    @Test
    void anAnswerCanBeChangedWhileTheExtraTimeLasts() {
        bothInTheFirstSession();
        now = T0.plusSeconds(46 * MIN);
        service.refresh(room);

        as(afsaneh);
        service.vote(5L, true);
        service.vote(5L, false);

        assertFalse(afsanehLink.getBreakVote());
        service.requireCanWrite(room, afsaneh);
    }

    @Test
    void aSessionWithoutAConversationDoesNotCount() {
        bothInTheFirstSession();
        now = T0.plusSeconds(61 * MIN);

        service.refresh(room);

        assertFalse(room.getSessionActive());
        assertEquals(0, room.getSessionNumber(), "its number is used again");
        assertNull(room.getSessionEndedAt(), "there is nothing to rest from");
        assertFalse(records.get(0).getCompleted());
        assertEquals(SessionEndReason.EMPTY, records.get(0).getEndReason());
        verify(summaries, never()).save(any(SessionSummary.class));
        verify(realtime).saveSystemAndPublish(eq(5L), contains("بدون گفتگو"));
        assertEquals(0, service.describe(room).getCompletedSessions());
    }

    @Test
    void closingClearsEveryoneAtOnceSoNothingReopensWhileTheSummaryIsWritten() {
        bothInTheFirstSession();
        someoneWrote(T0.plusSeconds(10 * MIN));
        now = T0.plusSeconds(46 * MIN);
        service.refresh(room);
        when(summaryService.summarise(any(), any(), anyInt(), anyString())).thenAnswer(call -> {
            // a page refresh arrives while the summary is being written
            service.refresh(room);
            return "خلاصه";
        });

        as(afsaneh);
        service.vote(5L, true);
        as(hossein);
        service.vote(5L, true);

        assertFalse(room.getSessionActive());
        assertEquals(1, room.getSessionNumber() == null ? 1 : room.getSessionNumber());
        verify(events, never()).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------------ the next session

    private void firstSessionClosedAt(Instant endedAt) {
        room.setSessionActive(false);
        room.setSessionNumber(1);
        room.setSessionEndedAt(endedAt);
        SessionRecord first = new SessionRecord();
        first.setChatRoomId(5L);
        first.setSessionNumber(1);
        first.setOpenedAt(endedAt.minusSeconds(50 * MIN));
        first.setStartedAt(endedAt.minusSeconds(50 * MIN));
        first.setEndedAt(endedAt);
        first.setCompleted(true);
        first.setCompletedOrdinal(1);
        records.add(first);
        afsanehLink.setSessionConsented(true);
        hosseinLink.setSessionConsented(true);
        SessionSummary previous = new SessionSummary();
        previous.setSummary("خلاصه‌ی جلسه‌ی قبل");
        when(summaries.findTopByChatRoomIdOrderBySessionNumberDesc(5L)).thenReturn(Optional.of(previous));
    }

    @Test
    void theNextSessionCannotOpenBeforeTheShortBreak() {
        firstSessionClosedAt(T0);
        now = T0.plusSeconds(14 * MIN);

        SessionOnBreakException error = assertThrows(SessionOnBreakException.class, () -> service.enterSession(5L));

        assertTrue(error.getMessage().contains("دقیقه"));
        assertNull(afsanehLink.getSessionReadyAt());
        assertEquals(T0.plusSeconds(15 * MIN).toEpochMilli(), service.describe(room).getNextAvailableAt());
        assertThrows(SessionOnBreakException.class, () -> service.requireCanWrite(room, afsaneh));
    }

    @Test
    void theFirstToPressStartOpensTheSessionAndMayTalkWhileTheOtherIsAwaited() {
        firstSessionClosedAt(T0);
        now = T0.plusSeconds(16 * MIN);

        as(afsaneh);
        SessionInfo info = service.enterSession(5L);

        assertTrue(info.getActive());
        assertEquals(2, room.getSessionNumber());
        assertNull(room.getSessionStartedAt(), "the 45 minutes wait for Hossein");
        assertEquals(now.plusSeconds(5 * MIN).toEpochMilli(), info.getWaitEndsAt());
        assertEquals(42, room.getSessionStartSequence());
        service.requireCanWrite(room, afsaneh);
        SessionOnBreakException notIn = assertThrows(SessionOnBreakException.class,
                () -> service.requireCanWrite(room, hossein));
        assertEquals(SessionOnBreakException.ENTRY, notIn.getCode());

        SessionMoment moment = lastMoment();
        assertTrue(moment.directorNote().contains("SESSION_OPENED session=2 completed_sessions=1"), moment.directorNote());
        assertTrue(moment.directorNote().contains("Entered now: OWNER (افسانه)"));
        assertTrue(moment.directorNote().contains("Not in yet: MEMBER (حسین)"));
        assertTrue(moment.directorNote().contains("خلاصه‌ی جلسه‌ی قبل"), "the counselor knows where the work stands");
        assertTrue(moment.v2Cue().startsWith("[BLOCK]"));
        assertEquals(2, records.size());
    }

    @Test
    void theFortyFiveMinutesStartWhenTheSecondPressesStart() {
        firstSessionClosedAt(T0);
        now = T0.plusSeconds(16 * MIN);
        as(afsaneh);
        service.enterSession(5L);

        now = now.plusSeconds(3 * MIN);
        as(hossein);
        service.enterSession(5L);

        assertEquals(now, room.getSessionStartedAt());
        SessionMoment moment = lastMoment();
        assertTrue(moment.directorNote().contains("PARTNER_ENTERED_SESSION session=2"), moment.directorNote());
        assertTrue(moment.directorNote().contains("Entered now: MEMBER (حسین)"));
        assertTrue(moment.directorNote().contains("Already in: OWNER (افسانه)"));
        assertTrue(moment.directorNote().contains("start now"));
        verify(realtime, never()).savePrivateSystemAndPublish(anyLong(), anyLong(), anyString());
    }

    @Test
    void afterTheWaitTheSessionStartsAndTheLatecomerIsToldPrivately() {
        firstSessionClosedAt(T0);
        now = T0.plusSeconds(16 * MIN);
        as(afsaneh);
        service.enterSession(5L);

        now = now.plusSeconds(5 * MIN + 1);
        service.refresh(room);

        assertEquals(now, room.getSessionStartedAt());
        ArgumentCaptor<String> notice = ArgumentCaptor.forClass(String.class);
        verify(realtime).savePrivateSystemAndPublish(eq(5L), eq(3L), notice.capture());
        assertTrue(notice.getValue().startsWith("حسین، ما ۵ دقیقه منتظرت بودیم"), notice.getValue());
        assertTrue(notice.getValue().contains("«شروع»"));

        now = now.plusSeconds(12 * MIN);
        as(hossein);
        service.enterSession(5L);
        assertTrue(lastMoment().directorNote().contains("running for 12 minutes without them"));
    }

    @Test
    void pressingStartAgainChangesNothing() {
        firstSessionClosedAt(T0);
        now = T0.plusSeconds(16 * MIN);
        as(afsaneh);
        service.enterSession(5L);
        Instant entered = afsanehLink.getSessionReadyAt();

        now = now.plusSeconds(MIN);
        service.enterSession(5L);

        assertEquals(entered, afsanehLink.getSessionReadyAt());
        verify(events, times(1)).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------------ at most three sessions in 24 hours

    private void heldSession(int number, Instant openedAt) {
        SessionRecord record = new SessionRecord();
        record.setChatRoomId(5L);
        record.setSessionNumber(number);
        record.setOpenedAt(openedAt);
        record.setStartedAt(openedAt);
        record.setEndedAt(openedAt.plusSeconds(60 * MIN));
        record.setCompleted(true);
        records.add(record);
    }

    @Test
    void noMoreThanThreeSessionsInTwentyFourHours() {
        room.setSessionActive(false);
        room.setSessionNumber(3);
        afsanehLink.setSessionConsented(true);
        heldSession(1, T0.minusSeconds(10 * 3600));
        heldSession(2, T0.minusSeconds(6 * 3600));
        heldSession(3, T0.minusSeconds(2 * 3600));
        room.setSessionEndedAt(T0.minusSeconds(3600));

        SessionOnBreakException error = assertThrows(SessionOnBreakException.class, () -> service.enterSession(5L));

        assertEquals(SessionOnBreakException.LIMIT, error.getCode());
        SessionInfo info = service.describe(room);
        assertTrue(info.getLimitReached());
        // the oldest of the three leaves the window 24 hours after it opened
        assertEquals(T0.plusSeconds(14 * 3600).toEpochMilli(), info.getLimitUntil());
        assertTrue(error.getMessage().contains("ساعت"), error.getMessage());

        now = T0.plusSeconds(14 * 3600 + 1);
        SessionInfo later = service.enterSession(5L);
        assertTrue(later.getActive());
        assertEquals(4, room.getSessionNumber());
    }

    @Test
    void aSessionWithoutAConversationIsNotCountedForTheLimit() {
        room.setSessionActive(false);
        room.setSessionNumber(2);
        afsanehLink.setSessionConsented(true);
        heldSession(1, T0.minusSeconds(10 * 3600));
        heldSession(2, T0.minusSeconds(6 * 3600));
        SessionRecord empty = new SessionRecord();
        empty.setChatRoomId(5L);
        empty.setSessionNumber(3);
        empty.setOpenedAt(T0.minusSeconds(3 * 3600));
        empty.setEndedAt(T0.minusSeconds(2 * 3600));
        empty.setCompleted(false);
        empty.setEndReason(SessionEndReason.EMPTY);
        records.add(empty);

        assertTrue(service.enterSession(5L).getActive());
        assertEquals(3, room.getSessionNumber());
    }

    @Test
    void theClosingWordsSayWhenTheNextSessionMayStartWhenTheLimitIsReached() {
        heldSession(1, T0.minusSeconds(10 * 3600));
        heldSession(2, T0.minusSeconds(6 * 3600));
        afsanehLink.setSessionConsented(true);
        hosseinLink.setSessionConsented(true);
        room.setSessionNumber(3);
        room.setSessionActive(true);
        as(afsaneh);
        service.enterByConsent(room, afsaneh);
        service.enterByConsent(room, hossein);
        service.refresh(room);
        someoneWrote(T0.plusSeconds(20 * MIN));

        now = T0.plusSeconds(61 * MIN);
        service.refresh(room);

        verify(realtime).saveAgentAndPublish(eq(5L), eq(9L), contains("حداکثر ۳ جلسه"));
    }

    // ------------------------------------------------------------ ending for good and leaving

    @Test
    void aConversationTheOwnerEndedTakesNoMoreMessagesVotesOrSessions() {
        bothInTheFirstSession();
        room.setStatus(com.coupleai.coupleai.beinema.Enum.ChatRoomStatus.ENDED);

        SessionOnBreakException closed = assertThrows(SessionOnBreakException.class,
                () -> service.requireCanWrite(room, hossein));
        assertEquals(SessionOnBreakException.CLOSED, closed.getCode());
        as(afsaneh);
        assertEquals(SessionOnBreakException.CLOSED,
                assertThrows(SessionOnBreakException.class, () -> service.enterSession(5L)).getCode());
        assertEquals(SessionOnBreakException.CLOSED,
                assertThrows(SessionOnBreakException.class, () -> service.vote(5L, true)).getCode());
    }

    @Test
    void whoLeftReadsButDoesNotWriteAndIsNotCountedInTheSession() {
        bothInTheFirstSession();
        hosseinLink.setMemberStatus(com.coupleai.coupleai.beinema.Enum.ChatRoomMemberStatus.LEFT);

        assertEquals(SessionOnBreakException.LEFT,
                assertThrows(SessionOnBreakException.class, () -> service.requireCanWrite(room, hossein)).getCode());
        service.requireCanWrite(room, afsaneh);

        // the break offer at 45 minutes: the owner alone decides it
        now = T0.plusSeconds(45 * MIN);
        someoneWrote(T0.plusSeconds(10 * MIN));
        service.refresh(room);
        as(afsaneh);
        service.vote(5L, true);
        assertEquals(Boolean.FALSE, room.getSessionActive(), "the owner alone took the break, so the session ended");
    }

    @Test
    void theOwnerEndingForGoodClosesTheOpenSessionWithoutWordsAboutTheNext() {
        bothInTheFirstSession();
        someoneWrote(T0.plusSeconds(5 * MIN));
        now = T0.plusSeconds(20 * MIN);
        room.setStatus(com.coupleai.coupleai.beinema.Enum.ChatRoomStatus.ENDED);

        service.closeForGood(room, afsaneh);

        assertEquals(Boolean.FALSE, room.getSessionActive());
        assertEquals(com.coupleai.coupleai.beinema.Enum.SessionEndReason.ENDED_FOR_GOOD, records.get(0).getEndReason());
        verify(realtime, never()).saveAgentAndPublish(anyLong(), anyLong(), contains("جلسه‌ی بعد"));
    }
}
