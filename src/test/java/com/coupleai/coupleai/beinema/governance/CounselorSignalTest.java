package com.coupleai.coupleai.beinema.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounselorSignalTest {

    @Test
    void readsReactionFromSilentDirectorPacket() {
        CounselorSignal signal = CounselorSignal.read(
                "ACTION=WAIT|ROUTE=SILENT|WHO=A|GOV=NONE|REACT=listen|STICKER=NONE\nBRIEF=\nNOTES=STAGE=S2"
        );
        assertEquals("LISTEN", signal.reactionToken());
        assertNull(signal.stickerToken());
        assertTrue(signal.present());
    }

    @Test
    void readsStickerOnlyWhenNoReaction() {
        CounselorSignal signal = CounselorSignal.read("ACTION=WAIT|REACT=NONE|STICKER=thinking\nBRIEF=");
        assertNull(signal.reactionToken());
        assertEquals("THINKING", signal.stickerToken());
    }

    @Test
    void unknownTokensAndOrdinaryRepliesAreIgnored() {
        assertFalse(CounselorSignal.read("ACTION=WAIT|REACT=PARTY").present());
        assertFalse(CounselorSignal.read("ACTION=WAIT|REACT=THINK").present());
        // names of the earlier sets, no longer drawn
        assertFalse(CounselorSignal.read("ACTION=WAIT|REACT=NOWORRY").present());
        assertFalse(CounselorSignal.read("ACTION=WAIT|STICKER=AGREE").present());
        assertFalse(CounselorSignal.read("سلام، دارم گوش می‌دم.").present());
        assertFalse(CounselorSignal.read(null).present());
    }

    @Test
    void stickerContentRoundTrips() {
        String content = CounselorSignal.stickerContent("THANKS");
        assertTrue(CounselorSignal.isSticker(content));
        assertEquals("THANKS", CounselorSignal.stickerName(content));
        assertEquals(CounselorSignal.emojiFor("THANKS"), CounselorSignal.stripSticker(content));
    }

    @Test
    void onlyTheTwentyFiveStickersOfTheCompanionsAreKnown() {
        assertEquals("WAITFORME", CounselorSignal.normalize("waitforme"));
        assertEquals("SORRYUPSET", CounselorSignal.normalize("SorryUpset"));
        // the earlier sets are gone: no companion has their pictures
        assertNull(CounselorSignal.normalize("NEUTRAL"));
        assertNull(CounselorSignal.normalize("CALM_BOTH"));
        assertNull(CounselorSignal.normalize("WRAP_UP"));
        assertNull(CounselorSignal.normalize("LOVE"));
        assertNull(CounselorSignal.normalize("AGREE"));
        // the session's own moments use two of the 25
        assertEquals(CounselorSignal.WAITING_PARTNER, CounselorSignal.normalize(CounselorSignal.WAITING_PARTNER));
        assertEquals(CounselorSignal.WRAP_UP, CounselorSignal.normalize(CounselorSignal.WRAP_UP));
    }
}
