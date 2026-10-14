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
        // exists in the artwork but a specialist would not send it
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
}
