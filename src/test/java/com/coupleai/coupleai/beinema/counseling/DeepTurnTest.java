package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeepTurnTest {

    @Test
    void theDeepLaneHasItsOwnMarkOnTheSamePacket() {
        String packet = DirectorPacketBuilder.MODE + "\n[TURN]\nsession=1";
        String deep = DeepTurn.deep(packet);
        assertEquals(DeepTurn.MODE + "\n[TURN]\nsession=1", deep);
        // the quick director's entry looks for its own mark: the deep packet never reaches it
        assertFalse(deep.contains(DirectorPacketBuilder.MODE));
    }
}
