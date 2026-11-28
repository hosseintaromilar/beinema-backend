package com.coupleai.coupleai.beinema.chat;

import com.coupleai.coupleai.beinema.Entity.*;
import com.coupleai.coupleai.beinema.Repository.*;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageHistoryVisibilityTest {
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRoomParticipantRepository participantRepository;
    @Mock MessageRepository messageRepository;
    @Mock ChatRealtimeService chatRealtimeService;
    @Mock CurrentUserService currentUserService;
    @InjectMocks MessageServiceImpl service;

    @Test
    void newMemberOnlyReceivesMessagesFromTheirEntryAndTheirOwnPrivateMessages() {
        User user = User.builder().build();
        user.setId(8L);
        ChatRoom room = ChatRoom.builder().build();
        room.setId(3L);
        ChatRoomParticipant participant = ChatRoomParticipant.builder()
                .chatRoom(room).user(user).visibleFromSequence(12).build();
        Message old = Message.builder().sequenceNumber(11).build();
        Message fresh = Message.builder().sequenceNumber(12).build();
        Message privateForOther = Message.builder().sequenceNumber(13).visibleToUserId(9L).build();
        Message privateForMe = Message.builder().sequenceNumber(14).visibleToUserId(8L).build();
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(chatRoomRepository.findById(3L)).thenReturn(Optional.of(room));
        when(participantRepository.existsByChatRoomAndUser(room, user)).thenReturn(true);
        when(participantRepository.findAllByChatRoom(room)).thenReturn(List.of(participant));
        when(messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(room))
                .thenReturn(List.of(old, fresh, privateForOther, privateForMe));

        service.getMessages(3L);

        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatRealtimeService).toResponses(eq(room), messages.capture());
        assertEquals(List.of(fresh, privateForMe), messages.getValue());
    }
}
