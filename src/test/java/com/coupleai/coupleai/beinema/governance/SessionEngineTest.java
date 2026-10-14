package com.coupleai.coupleai.beinema.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionEngineTest {

    private final GovernancePacketParser parser = new GovernancePacketParser();

    @Test
    void legacyGovernanceMarkerStaysHidden() {
        GovernanceDecision decision = parser.parse(
                "[[GOV|GOV=NONE|TARGET=NONE|DURATION=0]]\nاین جلسه مشاوره زوج است."
        );
        assertEquals(GovernanceAction.NONE, decision.governanceAction());
        assertEquals("این جلسه مشاوره زوج است.", decision.visibleText());
    }

    @Test
    void packetMarkersAreHiddenAndExploreIsAccepted() {
        String raw = "[[GOV|GOV=NONE|TARGET=NONE|DURATION=0]]\n"
                + "[[PKT|DOMAIN=RELATION|FOCUS=A|MOVE=EXPLORE|DEPTH=1|CONF=MED]]\n"
                + "بگو این انتخاب الان چه می‌کند.";
        GovernanceDecision decision = parser.parse(raw);
        assertFalse(decision.visibleText().contains("[[PKT"));
        assertEquals("بگو این انتخاب الان چه می‌کند.", decision.visibleText());
        assertTrue(SessionPacketParser.execution(raw).acceptable());
        assertEquals("EXPLORE", SessionPacketParser.execution(raw).action());
    }

    @Test
    void incompletePacketIsRejected() {
        SessionPacket packet = SessionPacketParser.execution("[[PKT|DOMAIN=RELATION|MOVE=ASK]]");
        assertTrue(packet.present());
        assertFalse(packet.acceptable());
        assertEquals("incomplete", packet.rejection());
    }

    @Test
    void invalidFocusIsRejected() {
        SessionPacket packet = SessionPacketParser.execution(
                "[[PKT|DOMAIN=RELATION|FOCUS=Z|MOVE=ASK]]"
        );
        assertFalse(packet.acceptable());
        assertEquals("invalid-target", packet.rejection());
    }

}
