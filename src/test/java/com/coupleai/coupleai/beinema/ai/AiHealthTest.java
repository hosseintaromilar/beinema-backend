package com.coupleai.coupleai.beinema.ai;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiHealthTest {

    private final ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    private final AiIncidentRepository incidents = mock(AiIncidentRepository.class);
    private final AiSessionPauseRepository pauses = mock(AiSessionPauseRepository.class);
    private final AdminAlertRepository alerts = mock(AdminAlertRepository.class);
    private final AiHealth health = new AiHealth(rooms, incidents, pauses, alerts);

    AiHealthTest() {
        when(incidents.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void errorsAreSortedByKind() {
        assertEquals(AiError.NO_CREDIT, AiError.ofStatus(402, ""));
        assertEquals(AiError.NO_CREDIT, AiError.ofStatus(403, "{\"message\":\"Insufficient credit\"}"));
        assertEquals(AiError.RATE_LIMIT, AiError.ofStatus(429, ""));
        assertEquals(AiError.UPSTREAM, AiError.ofStatus(502, ""));
        assertEquals(AiError.BAD_REQUEST, AiError.ofStatus(400, "bad field"));
        assertEquals(AiError.TIMEOUT, AiError.of(new RuntimeException(new HttpTimeoutException("slow"))));
        assertEquals(AiError.NO_CREDIT, AiError.of(new RuntimeException(new MetisCallException(AiError.NO_CREDIT, "x"))));
    }

    @Test
    void oneNoCreditOpensTheCircuitAndASuccessClosesIt() {
        health.failed(new MetisCallException(AiError.NO_CREDIT, "402"));
        assertTrue(health.down());
        assertEquals(AiHealth.DOWN, health.status().ai());

        health.succeeded();
        assertFalse(health.down());
        assertEquals(AiHealth.OK, health.status().ai());
    }

    @Test
    void fiveServiceFailuresInAMinuteOpenIt_fourOnlyDegrade() {
        for (int i = 0; i < 4; i++) {
            health.failed(new MetisCallException(AiError.UPSTREAM, "502"));
        }
        assertFalse(health.down());
        assertEquals(AiHealth.DEGRADED, health.status().ai());
        health.failed(new MetisCallException(AiError.UPSTREAM, "502"));
        assertTrue(health.down());
    }

    @Test
    void ourOwnBadRequestNeverOpensIt() {
        for (int i = 0; i < 10; i++) {
            health.failed(new MetisCallException(AiError.BAD_REQUEST, "400"));
        }
        assertFalse(health.down());
    }

    @Test
    void aRunningSessionGetsTheOutageBack() {
        ChatRoom room = new ChatRoom();
        room.setId(9L);
        room.setSessionNumber(2);
        room.setSessionActive(true);
        Instant started = Instant.now().minus(Duration.ofMinutes(10));
        room.setSessionStartedAt(started);
        when(rooms.findRunning(ChatRoomStatus.ACTIVE)).thenReturn(List.of(room));
        when(rooms.findById(9L)).thenReturn(Optional.of(room));

        health.failed(new MetisCallException(AiError.NO_CREDIT, "402"));
        health.reset();

        assertTrue(room.getSessionStartedAt().isAfter(started), "the clock was moved on by the outage");
        verify(pauses).save(any(AiSessionPause.class));
    }
}
