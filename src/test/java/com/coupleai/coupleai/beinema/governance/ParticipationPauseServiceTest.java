package com.coupleai.coupleai.beinema.governance;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.GovernanceLog;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Exception.CommitmentRequiredException;
import com.coupleai.coupleai.beinema.Exception.ParticipationPausedException;
import com.coupleai.coupleai.beinema.Exception.SessionEndedException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.GovernanceLogRepository;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParticipationPauseServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomParticipantRepository participantRepository;
    @Mock
    private GovernanceLogRepository governanceLogRepository;
    @Mock
    private ChatRoomEventHub chatRoomEventHub;
    @Mock
    private ChatRealtimeService chatRealtimeService;

    private ParticipationPauseService service;
    private ChatRoom room;
    private User sara;
    private User ali;
    private ChatRoomParticipant owner;
    private ChatRoomParticipant member;

    @BeforeEach
    void setUp() {
        GovernanceProperties properties = new GovernanceProperties();
        properties.setPauseMinSeconds(30);
        properties.setPauseMaxSeconds(300);
        properties.setPauseDefaultSeconds(45);
        service = new ParticipationPauseService(
                chatRoomRepository,
                participantRepository,
                governanceLogRepository,
                chatRoomEventHub,
                chatRealtimeService,
                properties,
                null,
                null,
                null
        );
        room = ChatRoom.builder().status(ChatRoomStatus.ACTIVE).createdBy(1L).title("s").build();
        room.setId(44L);
        sara = user(1L, "سارا");
        ali = user(2L, "علی");
        owner = ChatRoomParticipant.builder().chatRoom(room).user(sara).role(ParticipantRole.OWNER).build();
        member = ChatRoomParticipant.builder().chatRoom(room).user(ali).role(ParticipantRole.MEMBER).build();
        when(governanceLogRepository.save(any(GovernanceLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantRepository.save(any(ChatRoomParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantRepository.findAllByChatRoom(room)).thenReturn(List.of(owner, member));
        when(participantRepository.countByChatRoom(room)).thenReturn(2L);
    }

    @Test
    void sendingWhilePausedIsRejectedByBackend() {
        owner.setPausedUntil(Instant.now().plusSeconds(80));
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        ParticipationPausedException exception = assertThrows(
                ParticipationPausedException.class,
                () -> service.requireCanSend(room, sara)
        );
        assertTrue(exception.getRemainingSeconds() > 0);
    }

    @Test
    void expiredPauseWithoutCommitmentResumesAutomatically() {
        owner.setPausedUntil(Instant.now().minusSeconds(5));
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        ParticipationPauseService.ResumeHint hint = service.requireCanSend(room, sara);
        assertNull(owner.getPausedUntil());
        assertTrue(!hint.handshake());
    }

    @Test
    void expiredPauseRequiresCommitmentBeforeSend() {
        owner.setPausedUntil(Instant.now().minusSeconds(5));
        owner.setReentryPending(true);
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        assertThrows(CommitmentRequiredException.class, () -> service.requireCanSend(room, sara));
        verify(chatRealtimeService).saveSystemAndPublish(eq(44L), any());
    }

    @Test
    void acceptingCommitmentAllowsSend() {
        owner.setReentryPending(true);
        owner.setCommitmentOffered(true);
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        service.acceptReentry(room, sara);
        assertTrue(Boolean.TRUE.equals(owner.getReentryHandshake()));
        ParticipationPauseService.ResumeHint hint = service.requireCanSend(room, sara);
        assertTrue(hint.handshake());
        service.requireCanSend(room, sara);
    }

    @Test
    void missingSessionConsentBlocksSend() {
        owner.setSessionConsented(false);
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        CommitmentRequiredException exception = assertThrows(
                CommitmentRequiredException.class,
                () -> service.requireCanSend(room, sara)
        );
        assertEquals(CommitmentRequiredException.SESSION_CONSENT, exception.getCode());
        assertTrue(Boolean.TRUE.equals(owner.getConsentOffered()));
        verify(chatRealtimeService, never()).saveSystemAndPublish(any(), any());
    }

    @Test
    void legacyNullConsentStillAllowsSend() {
        owner.setSessionConsented(null);
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        service.requireCanSend(room, sara);
    }

    @Test
    void anAgreedPartnerIsNotLockedByThePendingOne() {
        owner.setSessionConsented(false);
        owner.setConsentOffered(true);
        member.setSessionConsented(false);
        member.setConsentOffered(true);
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        when(participantRepository.findAllByChatRoom(room)).thenReturn(List.of(owner, member));
        when(participantRepository.countByChatRoom(room)).thenReturn(2L);
        service.acceptSessionConsent(room, sara);
        assertTrue(Boolean.TRUE.equals(owner.getSessionConsented()));
        assertTrue(Boolean.FALSE.equals(member.getSessionConsented()));
        // The partner who already agreed is never locked by the other one's pending consent.
        service.requireCanSend(room, sara);
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 2L)).thenReturn(Optional.of(member));
        CommitmentRequiredException blocked = assertThrows(
                CommitmentRequiredException.class,
                () -> service.requireCanSend(room, ali)
        );
        assertEquals(CommitmentRequiredException.SESSION_CONSENT, blocked.getCode());
        service.acceptSessionConsent(room, ali);
        service.requireCanSend(room, sara);
        service.requireCanSend(room, ali);
    }

    @Test
    void afterEachConsentTheCounselorThanksAndAsksTheFirstQuestion() {
        service.greeter = Runnable::run;
        service.thanksTypingMillis = 0;
        owner.setSessionConsented(false);
        member.setSessionConsented(false);
        when(chatRoomRepository.findById(44L)).thenReturn(Optional.of(room));
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 2L)).thenReturn(Optional.of(member));

        service.acceptSessionConsent(room, sara);
        verify(chatRealtimeService).saveAgentAndPublish(eq(44L), eq(0L),
                org.mockito.ArgumentMatchers.argThat(text -> text.startsWith("سارا، خوشحالم که این مسیر را با هم شروع می‌کنیم")
                        && text.contains("چه چیزی تو را به بین‌ما آورده")));

        service.acceptSessionConsent(room, ali);
        verify(chatRealtimeService).saveAgentAndPublish(eq(44L), eq(0L),
                org.mockito.ArgumentMatchers.argThat(text -> text.startsWith("علی، خوشحالم")
                        && text.contains("هر دو نفرتان اینجا هستید")));
    }

    @Test
    void endedSessionRejectsMessages() {
        room.setStatus(ChatRoomStatus.ENDED);
        assertThrows(SessionEndedException.class, () -> service.requireCanSend(room, sara));
    }

    @Test
    void archivedSessionRejectsMessages() {
        room.setStatus(ChatRoomStatus.ARCHIVED);
        assertThrows(SessionEndedException.class, () -> service.requireCanSend(room, sara));
    }

    @Test
    void pauseUserDoesNotPausePartner() {
        when(participantRepository.findAllByChatRoomIdForUpdate(44L)).thenReturn(List.of(owner, member));
        service.apply(room, new GovernanceDecision(
                "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.OWNER, 60, "repeat insult", "pause", false
        ));
        assertNotNull(owner.getPausedUntil());
        assertNull(member.getPausedUntil());
        verify(chatRoomEventHub).publishNamed(eq(44L), eq("participation_paused"), any());
    }

    @Test
    void pauseBothPausesEveryone() {
        when(participantRepository.findAllByChatRoomIdForUpdate(44L)).thenReturn(List.of(owner, member));
        service.apply(room, new GovernanceDecision(
                "INTERVENE", GovernanceAction.PAUSE_BOTH, GovernanceTarget.BOTH, 90, "escalation", "pause", false
        ));
        assertNotNull(owner.getPausedUntil());
        assertNotNull(member.getPausedUntil());
    }

    @Test
    void successivePausesExtendTheLaterOne() {
        owner.setPausedUntil(Instant.now().plusSeconds(40));
        when(participantRepository.findAllByChatRoomIdForUpdate(44L)).thenReturn(List.of(owner, member));
        service.apply(room, new GovernanceDecision(
                "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.OWNER, 180, "again", "pause", false
        ));
        assertTrue(owner.getPausedUntil().isAfter(Instant.now().plusSeconds(100)));
    }

    @Test
    void invalidDurationIsClampedNotRejected() {
        when(participantRepository.findAllByChatRoomIdForUpdate(44L)).thenReturn(List.of(owner, member));
        service.apply(room, new GovernanceDecision(
                "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.OWNER, 99999999, "x", "pause", false
        ));
        long remaining = owner.getPausedUntil().getEpochSecond() - Instant.now().getEpochSecond();
        assertTrue(remaining <= 300);
        assertTrue(remaining >= 30);
    }

    @Test
    void invalidTargetIsNotEnforced() {
        when(participantRepository.findAllByChatRoomIdForUpdate(44L)).thenReturn(List.of(owner, member));
        service.apply(room, new GovernanceDecision(
                "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.NONE, 60, "x", "pause", false
        ));
        assertNull(owner.getPausedUntil());
        verify(chatRoomEventHub, never()).publishNamed(eq(44L), eq("participation_paused"), any());
    }

    @Test
    void endSessionChangesRoomStatus() {
        when(participantRepository.findAllByChatRoomIdForUpdate(44L)).thenReturn(List.of(owner, member));
        when(chatRoomRepository.saveAndFlush(room)).thenReturn(room);
        service.apply(room, new GovernanceDecision(
                "END_SESSION", GovernanceAction.END_SESSION, GovernanceTarget.BOTH, 0, "out of control", "end", false
        ));
        assertEquals(ChatRoomStatus.ENDED, room.getStatus());
        verify(chatRoomEventHub).publishNamed(eq(44L), eq("session_ended"), any());
    }

    @Test
    void governanceOnInactiveRoomIsIgnored() {
        room.setStatus(ChatRoomStatus.ENDED);
        service.apply(room, new GovernanceDecision(
                "INTERVENE", GovernanceAction.PAUSE_BOTH, GovernanceTarget.BOTH, 60, "x", "x", false
        ));
        verify(participantRepository, never()).findAllByChatRoomIdForUpdate(any());
    }

    @Test
    void concurrentSendWhilePausedBothSeeRejection() throws Exception {
        owner.setPausedUntil(Instant.now().plusSeconds(30));
        when(participantRepository.findByChatRoomIdAndUserIdForUpdate(44L, 1L)).thenReturn(Optional.of(owner));
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);
        Runnable send = () -> {
            ready.countDown();
            try {
                ready.await(1, TimeUnit.SECONDS);
                service.requireCanSend(room, sara);
            } catch (ParticipationPausedException ignored) {
                rejected.incrementAndGet();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };
        new Thread(send).start();
        new Thread(send).start();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(2, rejected.get());
    }

    @Test
    void auditDoesNotNeedPrivateTranscript() {
        when(participantRepository.findAllByChatRoomIdForUpdate(44L)).thenReturn(List.of(owner, member));
        service.apply(room, new GovernanceDecision(
                "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.OWNER, 60, "Repeated insulting behavior", "pause", false
        ));
        ArgumentCaptor<GovernanceLog> captor = ArgumentCaptor.forClass(GovernanceLog.class);
        verify(governanceLogRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getValue().getReason() == null
                || !captor.getValue().getReason().contains("[OWNER"));
    }

    private static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }
}
