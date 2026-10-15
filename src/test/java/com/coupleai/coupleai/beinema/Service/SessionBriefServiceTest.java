package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionBriefServiceTest {

    private MessageRepository messages;
    private ChatRoomParticipantRepository participants;
    private MetisService metis;
    private SessionBriefService service;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        messages = mock(MessageRepository.class);
        participants = mock(ChatRoomParticipantRepository.class);
        metis = mock(MetisService.class);
        service = new SessionBriefService(messages, participants, metis);
        ReflectionTestUtils.setField(service, "rotateAfterMessages", 10);
        ReflectionTestUtils.setField(service, "keepRecent", 3);
        ReflectionTestUtils.setField(service, "insightBotId", "insight-bot");

        room = new ChatRoom();
        room.setId(7L);
    }

    private Message message(int seq, MessageSenderType type, Long senderId, String text) {
        Message message = new Message();
        message.setSequenceNumber(seq);
        message.setSenderType(type);
        message.setSenderId(senderId);
        message.setContent(text);
        message.setDeleted(false);
        return message;
    }

    @Test
    void rotatesOnlyAfterEnoughMessages() {
        when(messages.findMaxSequenceNumberByChatRoom(room)).thenReturn(Optional.of(9));
        assertFalse(service.shouldRotate(room));

        when(messages.findMaxSequenceNumberByChatRoom(room)).thenReturn(Optional.of(10));
        assertTrue(service.shouldRotate(room));
    }

    @Test
    void countsFromTheStartOfTheCurrentSession() {
        room.setAiSessionStartSequence(30);
        when(messages.findMaxSequenceNumberByChatRoom(room)).thenReturn(Optional.of(35));
        assertFalse(service.shouldRotate(room));

        when(messages.findMaxSequenceNumberByChatRoom(room)).thenReturn(Optional.of(40));
        assertTrue(service.shouldRotate(room));
    }

    @Test
    void waitsAfterAFailedAttempt() {
        when(messages.findMaxSequenceNumberByChatRoom(room)).thenReturn(Optional.of(50));
        assertTrue(service.shouldRotate(room));
        service.noteFailure(room);
        assertFalse(service.shouldRotate(room));
    }

    @Test
    void disabledWhenThresholdIsZero() {
        ReflectionTestUtils.setField(service, "rotateAfterMessages", 0);
        when(messages.findMaxSequenceNumberByChatRoom(room)).thenReturn(Optional.of(500));
        assertFalse(service.shouldRotate(room));
    }

    @Test
    void briefCarriesSummaryAndRecentMessagesWithoutTheMessageBeingSent() {
        User husband = new User();
        husband.setId(1L);
        husband.setName("حسین");
        ChatRoomParticipant participant = new ChatRoomParticipant();
        participant.setUser(husband);
        participant.setRole(ParticipantRole.OWNER);
        when(participants.findAllByChatRoom(room)).thenReturn(List.of(participant));

        List<Message> history = new ArrayList<>();
        history.add(message(1, MessageSenderType.USER, 1L, "سلام"));
        history.add(message(2, MessageSenderType.AGENT, 0L, "خوش اومدی"));
        history.add(message(3, MessageSenderType.USER, 1L, "من حس می‌کنم شنیده نمی‌شم"));
        history.add(message(4, MessageSenderType.AGENT, 0L, "این برات سنگینه"));
        history.add(message(5, MessageSenderType.USER, 1L, "پیام در حال ارسال"));
        when(messages.findAllByChatRoomOrderBySequenceNumberAsc(room)).thenReturn(history);

        MetisConversationResponse session = new MetisConversationResponse();
        session.setId("summary-session");
        when(metis.createConversation(anyString(), any())).thenReturn(session);
        when(metis.sendMessage(anyString(), anyString())).thenReturn("حسین می‌خواهد شنیده شود.");

        var brief = service.buildBrief(room, husband);

        assertEquals("حسین می‌خواهد شنیده شود.", brief.summary());
        String block = brief.block();
        assertTrue(block.contains("EVENT=SESSION_CONTINUED"));
        assertTrue(block.contains("BRIEF_SO_FAR:\nحسین می‌خواهد شنیده شود."));
        assertTrue(block.contains("END_OF_BRIEF"));
        assertTrue(block.contains("COUNSELOR: این برات سنگینه"));
        assertTrue(block.contains("من حس می‌کنم شنیده نمی‌شم"));
        // the message that is about to be sent travels on its own
        assertFalse(block.contains("پیام در حال ارسال"));
    }

    @Test
    void aJsonAnswerIsNotUsedAsABrief() {
        User husband = new User();
        husband.setId(1L);
        when(participants.findAllByChatRoom(room)).thenReturn(List.of());
        when(messages.findAllByChatRoomOrderBySequenceNumberAsc(room))
                .thenReturn(List.of(message(1, MessageSenderType.USER, 1L, "سلام")));
        MetisConversationResponse session = new MetisConversationResponse();
        session.setId("s");
        when(metis.createConversation(anyString(), any())).thenReturn(session);
        when(metis.sendMessage(anyString(), anyString())).thenReturn("{\"whereWeAre\":\"x\"}");

        assertThrows(IllegalStateException.class, () -> service.buildBrief(room, husband));
    }
}
