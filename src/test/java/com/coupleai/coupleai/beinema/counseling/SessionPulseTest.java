package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionPulseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

    private static LocalDateTime ago(long minutes) {
        return NOW.minusMinutes(minutes);
    }

    @Test
    void anUnansweredPersonGetsATurnTwiceThenIsLeftAlone() {
        assertEquals("ROOM_QUIET minutes=3 nudge=1", SessionPulse.decide(NOW, ago(3), ago(3), ago(5), 0, 3, 4, 1));
        assertEquals("ROOM_QUIET minutes=3 nudge=2", SessionPulse.decide(NOW, ago(3), ago(3), ago(5), 1, 3, 4, 1));
        assertNull(SessionPulse.decide(NOW, ago(3), ago(3), ago(5), 2, 3, 4, 1));
    }

    @Test
    void whenTheCompanionAskedTheTurnIsTheirs() {
        assertNull(SessionPulse.decide(NOW, ago(3), ago(9), ago(3), 0, 3, 4, 1), "one person is not asked again after 3 minutes");
        assertNull(SessionPulse.decide(NOW, ago(30), ago(31), ago(30), 0, 3, 4, 1), "nor after 30");
        assertNull(SessionPulse.decide(NOW, ago(3), ago(9), ago(3), 0, 3, 4, 2), "two are not asked again too soon");
        assertEquals("ROOM_QUIET minutes=6 nudge=1", SessionPulse.decide(NOW, ago(6), ago(9), ago(6), 0, 3, 4, 2));
        assertNull(SessionPulse.decide(NOW, ago(9), ago(12), ago(9), 1, 3, 4, 2), "and only once");
    }

    @Test
    void twoPeopleTalkingLongWithoutTheCompanionGiveItATurn() {
        assertEquals("LISTENED minutes=4", SessionPulse.decide(NOW, ago(1), ago(1), ago(4), 0, 3, 4, 2));
        assertNull(SessionPulse.decide(NOW, ago(1), ago(1), ago(2), 0, 3, 4, 2));
    }

    @Test
    void anEmptyRoomOrAFreshReplyAsksForNothing() {
        assertNull(SessionPulse.decide(NOW, null, null, null, 0, 3, 4, 2));
        assertNull(SessionPulse.decide(NOW, ago(1), ago(6), ago(1), 0, 3, 4, 2));
    }
}
