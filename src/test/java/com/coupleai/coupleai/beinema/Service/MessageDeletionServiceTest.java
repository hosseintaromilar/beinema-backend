package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDeletionServiceTest {

    private final MessageRepository messages = mock(MessageRepository.class);
    private final ChatRoomParticipantRepository participants = mock(ChatRoomParticipantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChatFileService files = mock(ChatFileService.class);
    private final ChatRoomEventHub hub = mock(ChatRoomEventHub.class);
    private final MessageDeletionService service = new MessageDeletionService(messages, participants, users, files, hub,
            mock(com.coupleai.coupleai.beinema.Repository.DeletionLogRepository.class));

    private final ChatRoom room = new ChatRoom();
    private final User me = new User();

    private Message message(Long senderId, MessageSenderType type) {
        room.setId(7L);
        me.setId(1L);
        Message message = Message.builder().chatRoom(room).senderId(senderId).senderType(type).content("سلام").build();
        message.setId(50L);
        when(users.findByEmail("me@x")).thenReturn(Optional.of(me));
        when(messages.findById(50L)).thenReturn(Optional.of(message));
        when(participants.findByChatRoomAndUser(room, me)).thenReturn(Optional.of(new ChatRoomParticipant()));
        return message;
    }

    @Test
    void theSenderErasesTheWordsAndTheFiles() {
        Message mine = message(1L, MessageSenderType.USER);

        service.deleteOwn(7L, 50L, "me@x");

        assertTrue(mine.getDeleted());
        assertEquals("", mine.getContent());
        verify(files).deleteMessageFiles(50L);
        verify(hub).publishNamed(7L, "message_deleted", Map.of("messageId", 50L));
    }

    @Test
    void nobodyDeletesTheOtherPersonsOrTheCounselorsMessage() {
        Message theirs = message(2L, MessageSenderType.USER);
        assertThrows(IllegalArgumentException.class, () -> service.deleteOwn(7L, 50L, "me@x"));
        assertFalse(Boolean.TRUE.equals(theirs.getDeleted()));

        Message counselor = message(1L, MessageSenderType.AGENT);
        assertThrows(IllegalArgumentException.class, () -> service.deleteOwn(7L, 50L, "me@x"));
        assertEquals("سلام", counselor.getContent());
        verify(hub, never()).publishNamed(any(), anyString(), any());
    }

    @Test
    void aMessageOfAnotherRoomIsNotFound() {
        message(1L, MessageSenderType.USER);
        assertThrows(IllegalArgumentException.class, () -> service.deleteOwn(8L, 50L, "me@x"));
    }
}
