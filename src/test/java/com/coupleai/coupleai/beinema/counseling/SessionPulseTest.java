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
    void aQuietRoomGetsATurnTwiceThenIsLeftAlone() {
        assertEquals("ROOM_QUIET minutes=3 nudge=1", SessionPulse.decide(NOW, ago(3), ago(3), ago(5), 0, 3, 4));
        assertEquals("ROOM_QUIET minutes=3 nudge=2", SessionPulse.decide(NOW, ago(3), ago(9), ago(3), 1, 3, 4));
        assertNull(SessionPulse.decide(NOW, ago(3), ago(9), ago(3), 2, 3, 4));
    }

    @Test
    void twoPeopleTalkingLongWithoutTheCompanionGiveItATurn() {
        assertEquals("LISTENED minutes=4", SessionPulse.decide(NOW, ago(1), ago(1), ago(4), 0, 3, 4));
        assertNull(SessionPulse.decide(NOW, ago(1), ago(1), ago(2), 0, 3, 4));
    }

    @Test
    void anEmptyRoomOrAFreshReplyAsksForNothing() {
        assertNull(SessionPulse.decide(NOW, null, null, null, 0, 3, 4));
        assertNull(SessionPulse.decide(NOW, ago(1), ago(6), ago(1), 0, 3, 4));
    }
}
