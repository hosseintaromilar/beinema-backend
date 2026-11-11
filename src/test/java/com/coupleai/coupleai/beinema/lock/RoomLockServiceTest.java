package com.coupleai.coupleai.beinema.lock;

import com.coupleai.coupleai.beinema.Entity.RoomLock;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.RoomLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomLockServiceTest {

    private final Map<String, RoomLock> table = new HashMap<>();
    private RoomLockService service;

    @BeforeEach
    void setUp() {
        RoomLockRepository repository = mock(RoomLockRepository.class);
        ChatRoomRepository rooms = mock(ChatRoomRepository.class);
        ChatRoomParticipantRepository participants = mock(ChatRoomParticipantRepository.class);
        when(rooms.existsById(anyLong())).thenReturn(true);
        when(participants.existsByChatRoom_IdAndUser_Id(7L, 1L)).thenReturn(true);
        when(participants.existsByChatRoom_IdAndUser_Id(7L, 2L)).thenReturn(true);
        when(repository.findByUserIdAndChatRoomId(anyLong(), anyLong()))
                .thenAnswer(call -> Optional.ofNullable(table.get(call.getArgument(0) + ":" + call.getArgument(1))));
        when(repository.existsByUserIdAndChatRoomId(anyLong(), anyLong()))
                .thenAnswer(call -> table.containsKey(call.getArgument(0) + ":" + call.getArgument(1)));
        when(repository.save(any(RoomLock.class))).thenAnswer(call -> {
            RoomLock lock = call.getArgument(0);
            table.put(lock.getUserId() + ":" + lock.getChatRoomId(), lock);
            return lock;
        });
        doAnswer(call -> {
            RoomLock lock = call.getArgument(0);
            table.remove(lock.getUserId() + ":" + lock.getChatRoomId());
            return null;
        }).when(repository).delete(any(RoomLock.class));
        service = new RoomLockService(repository, rooms, participants);
    }

    @Test
    void aPinIsFourToEightDigitsAndPersianDigitsCount() {
        assertEquals("1234", RoomLockService.pin("۱۲۳۴"));
        assertThrows(IllegalArgumentException.class, () -> RoomLockService.pin("12a4"));
        assertThrows(IllegalArgumentException.class, () -> RoomLockService.pin("123"));
    }

    @Test
    void theRightPinOpensTheRoomForThatPersonOnly() {
        String first = service.set(1L, 7L, "2468", null, null);
        assertTrue(service.isLocked(1L, 7L));
        assertFalse(service.isLocked(2L, 7L), "the partner's own entry is not locked");
        assertTrue(service.opens(first, 1L, 7L));
        assertFalse(service.opens(first, 2L, 7L), "a key belongs to one person");
        assertFalse(service.opens(first, 1L, 8L), "and to one room");

        String key = service.unlock(1L, 7L, "۲۴۶۸");
        assertTrue(service.opens(key, 1L, 7L));
        assertThrows(IllegalArgumentException.class, () -> service.unlock(1L, 7L, "1111"));
    }

    @Test
    void fiveWrongPinsBlockTheDoorEvenTheRightOne() {
        service.set(1L, 7L, "2468", null, null);
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> service.unlock(1L, 7L, "0000"));
        }
        IllegalArgumentException blocked = assertThrows(IllegalArgumentException.class, () -> service.unlock(1L, 7L, "2468"));
        assertTrue(blocked.getMessage().contains("دقیقه"));
    }

    @Test
    void changingOrRemovingNeedsThePinWithoutAnOpenKey() {
        service.set(1L, 7L, "2468", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.set(1L, 7L, "1357", "0000", null));
        assertThrows(IllegalArgumentException.class, () -> service.remove(1L, 7L, "0000", null));
        service.remove(1L, 7L, "2468", null);
        assertFalse(service.isLocked(1L, 7L));
    }

    @Test
    void aForgottenPinGoesOnlyAfterSigningInAgain() {
        service.set(1L, 7L, "2468", null, null);
        Instant before = Instant.now().minus(Duration.ofMinutes(5));
        assertThrows(IllegalArgumentException.class, () -> service.reset(1L, 7L, Instant.now()), "not asked yet");
        service.requestReset(1L, 7L);
        assertThrows(IllegalArgumentException.class, () -> service.reset(1L, 7L, before), "the old sign-in does not count");
        String key = service.reset(1L, 7L, Instant.now().plusSeconds(1));
        assertFalse(service.isLocked(1L, 7L));
        assertTrue(service.opens(key, 1L, 7L));
    }
}
