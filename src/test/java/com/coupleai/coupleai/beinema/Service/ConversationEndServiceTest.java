package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Feedback.FeedbackRequest;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.ServiceFeedback;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomMemberStatus;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.ServiceFeedbackRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationEndServiceTest {

    private static final Long ROOM = 7L;

    private ChatRoomRepository rooms;
    private ChatRoomParticipantRepository participants;
    private ServiceFeedbackRepository feedback;
    private CurrentUserService current;
    private ChatRealtimeService realtime;
    private SessionLifecycleService sessions;
    private ApplicationEventPublisher events;
    private ConversationEndService service;

    private ChatRoom room;
    private User afsaneh;
    private User hossein;
    private ChatRoomParticipant owner;
    private ChatRoomParticipant member;

    private static User user(long id, String name) {
        User user = User.builder().name(name).email(id + "@users.beinema").password("x")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ChatRoomParticipant link(User user, ParticipantRole role) {
        ChatRoomParticipant participant = new ChatRoomParticipant();
        participant.setChatRoom(room);
        participant.setUser(user);
        participant.setRole(role);
        return participant;
    }

    @BeforeEach
    void setUp() {
        rooms = mock(ChatRoomRepository.class);
        participants = mock(ChatRoomParticipantRepository.class);
        feedback = mock(ServiceFeedbackRepository.class);
        current = mock(CurrentUserService.class);
        realtime = mock(ChatRealtimeService.class);
        sessions = mock(SessionLifecycleService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ConversationEndService(rooms, participants, feedback, mock(UserRepository.class), current,
                realtime, sessions, events);

        afsaneh = user(1L, "افسانه");
        hossein = user(3L, "حسین");
        room = new ChatRoom();
        room.setId(ROOM);
        room.setTitle("دعواهای هر شب");
        room.setStatus(ChatRoomStatus.ACTIVE);
        room.setCreatedBy(afsaneh.getId());
        owner = link(afsaneh, ParticipantRole.OWNER);
        member = link(hossein, ParticipantRole.MEMBER);

        when(rooms.findById(ROOM)).thenReturn(Optional.of(room));
        when(participants.findByChatRoomAndUser(room, afsaneh)).thenReturn(Optional.of(owner));
        when(participants.findByChatRoomAndUser(room, hossein)).thenReturn(Optional.of(member));
        when(participants.findAllByChatRoom(room)).thenReturn(List.of(owner, member));
    }

    @Test
    void theOwnerEndsTheConversationForBoth() {
        when(current.getCurrentUser()).thenReturn(afsaneh);

        assertEquals("ENDED", service.end(ROOM).outcome());

        assertEquals(ChatRoomStatus.ENDED, room.getStatus());
        assertEquals(afsaneh.getId(), room.getEndedBy());
        verify(rooms).save(room);
        verify(realtime).saveSystemAndPublish(eq(ROOM), org.mockito.ArgumentMatchers.contains("پایان داد"));
        verify(sessions).closeForGood(room, afsaneh);
        verify(events, never()).publishEvent(any(Object.class));
        assertTrue(member.isActiveMember(), "the partner is not marked as left; the whole room is closed");
    }

    @Test
    void theOtherPersonLeavesAndTheOwnerGoesOnAlone() {
        when(current.getCurrentUser()).thenReturn(hossein);
        member.setSessionReadyAt(java.time.Instant.now());
        member.setBreakVote(true);

        assertEquals("LEFT", service.end(ROOM).outcome());

        assertEquals(ChatRoomMemberStatus.LEFT, member.getMemberStatus());
        assertTrue(member.getLeftAt() != null);
        assertNull(member.getSessionReadyAt(), "they are out of the running session");
        assertNull(member.getBreakVote());
        assertEquals(ChatRoomStatus.ACTIVE, room.getStatus(), "the room stays open for the owner");
        verify(sessions, never()).closeForGood(any(), any());
        ArgumentCaptor<Object> moment = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(moment.capture());
        String note = ((SessionMoment) moment.getValue()).directorNote();
        assertTrue(note.contains("PARTNER_LEFT"));
        assertTrue(note.contains("OWNER (افسانه) only"), "the counselor works with the owner alone");
    }

    @Test
    void endingTwiceChangesNothing() {
        when(current.getCurrentUser()).thenReturn(hossein);
        member.setMemberStatus(ChatRoomMemberStatus.LEFT);
        assertEquals("LEFT", service.end(ROOM).outcome());
        verify(realtime, never()).saveSystemAndPublish(anyLong(), anyString());

        room.setStatus(ChatRoomStatus.ENDED);
        when(current.getCurrentUser()).thenReturn(afsaneh);
        assertEquals("ENDED", service.end(ROOM).outcome());
        verify(sessions, never()).closeForGood(any(), any());
    }

    @Test
    void comingBackNeedsNoNewConsentAndTheCounselorWelcomesThem() {
        when(current.getCurrentUser()).thenReturn(hossein);
        member.setMemberStatus(ChatRoomMemberStatus.LEFT);
        member.setSessionConsented(true);

        service.rejoin(ROOM);

        assertTrue(member.isActiveMember());
        assertNull(member.getLeftAt());
        assertEquals(Boolean.TRUE, member.getSessionConsented(), "the rules are not asked again");
        verify(realtime).saveSystemAndPublish(ROOM, "حسین به گفتگو برگشت.");
        ArgumentCaptor<Object> moment = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(moment.capture());
        String note = ((SessionMoment) moment.getValue()).directorNote();
        assertTrue(note.contains("PARTNER_REJOINED") && note.contains("no rules"));
    }

    @Test
    void nobodyComesBackToAConversationTheOwnerEnded() {
        when(current.getCurrentUser()).thenReturn(hossein);
        member.setMemberStatus(ChatRoomMemberStatus.LEFT);
        room.setStatus(ChatRoomStatus.ENDED);

        assertThrows(IllegalArgumentException.class, () -> service.rejoin(ROOM));
        assertEquals(ChatRoomMemberStatus.LEFT, member.getMemberStatus());
    }

    @Test
    void theSurveyKeepsAnswersFromOneToFiveAndAComment() {
        when(current.getCurrentUser()).thenReturn(hossein);
        when(feedback.findByChatRoomIdAndUserId(ROOM, hossein.getId())).thenReturn(Optional.empty());
        FeedbackRequest request = new FeedbackRequest();
        request.setHeard(5);
        request.setFairness(4);
        request.setRecommend(3);
        request.setComment("  خیلی کمک کرد  ");

        service.saveFeedback(ROOM, request);

        ArgumentCaptor<ServiceFeedback> saved = ArgumentCaptor.forClass(ServiceFeedback.class);
        verify(feedback).save(saved.capture());
        assertEquals("MEMBER", saved.getValue().getRole());
        assertEquals(5, saved.getValue().getHeard());
        assertNull(saved.getValue().getProgress(), "a skipped question stays empty");
        assertEquals("خیلی کمک کرد", saved.getValue().getComment());
    }

    @Test
    void theSurveyRefusesAnswersOutsideTheScaleAndEmptyForms() {
        when(current.getCurrentUser()).thenReturn(afsaneh);
        FeedbackRequest wrong = new FeedbackRequest();
        wrong.setHeard(9);
        assertThrows(IllegalArgumentException.class, () -> service.saveFeedback(ROOM, wrong));
        assertThrows(IllegalArgumentException.class, () -> service.saveFeedback(ROOM, new FeedbackRequest()));
        verify(feedback, never()).save(any());
    }
}
