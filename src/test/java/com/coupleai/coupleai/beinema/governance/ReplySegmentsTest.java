package com.coupleai.coupleai.beinema.governance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplySegmentsTest {

    @Test
    void plainReplyIsOneSegment() {
        List<ReplySegments.Segment> parts = ReplySegments.split("سلام، دارم گوش می‌دم.");
        assertEquals(1, parts.size());
        assertEquals(0, parts.get(0).pauseSeconds());
    }

    @Test
    void breakMarkersCutTheReplyAndKeepPauseHints() {
        List<ReplySegments.Segment> parts = ReplySegments.split(
                "این مهمه.[[BREAK|PAUSE=2]]بذار بپرسم.\n[[BREAK]] چی شد؟"
        );
        assertEquals(3, parts.size());
        assertEquals("این مهمه.", parts.get(0).text());
        assertEquals(2, parts.get(1).pauseSeconds());
        assertEquals("چی شد؟", parts.get(2).text());
        assertEquals(0, parts.get(2).pauseSeconds());
    }

    @Test
    void emptyPartsAreDroppedAndPauseIsClamped() {
        List<ReplySegments.Segment> parts = ReplySegments.split(
                "[[BREAK|PAUSE=99]]  [[BREAK]]یک[[BREAK|PAUSE=99]]دو"
        );
        assertEquals(2, parts.size());
        assertEquals(ReplySegments.MAX_PAUSE_SECONDS, parts.get(1).pauseSeconds());
    }

    @Test
    void tooManySegmentsAreMergedIntoTheLastOne() {
        List<ReplySegments.Segment> parts = ReplySegments.split(
                "a[[BREAK]]b[[BREAK]]c[[BREAK]]d[[BREAK]]e[[BREAK]]f"
        );
        assertEquals(ReplySegments.MAX_SEGMENTS, parts.size());
        assertTrue(parts.get(parts.size() - 1).text().contains("e"));
        assertTrue(parts.get(parts.size() - 1).text().contains("f"));
    }

    @Test
    void plainJoinsSegmentsWithBlankLine() {
        assertEquals("یک\n\nدو", ReplySegments.plain("یک[[BREAK|PAUSE=1]]دو"));
        assertEquals("", ReplySegments.plain("[[BREAK]]"));
    }

    @Test
    void stickerMarkerBecomesItsOwnSegmentAndKnowsOnlyTheCatalog() {
        ReplySegments.Parsed parsed = ReplySegments.parse(
                "ممنون که گفتی.[[STICKER|NAME=thanks]] ادامه بده [[STICKER|NAME=party]]"
        );
        assertEquals(3, parsed.segments().size());
        assertEquals("ممنون که گفتی.", parsed.segments().get(0).text());
        assertTrue(parsed.segments().get(1).isSticker());
        assertEquals("THANKS", parsed.segments().get(1).stickerToken());
        assertEquals("ادامه بده", parsed.segments().get(2).text());
    }

    @Test
    void onlyTheFirstStickerIsKept() {
        ReplySegments.Parsed parsed = ReplySegments.parse(
                "[[STICKER|NAME=ACK]][[STICKER|NAME=BYE]]خب"
        );
        long stickers = parsed.segments().stream().filter(ReplySegments.Segment::isSticker).count();
        assertEquals(1, stickers);
    }

    @Test
    void replyMarkerSetsTheFlagAndStaysOutOfTheText() {
        ReplySegments.Parsed parsed = ReplySegments.parse("[[REPLY]]درباره حرف علی می‌گم.");
        assertTrue(parsed.replyToTrigger());
        assertEquals("درباره حرف علی می‌گم.", parsed.segments().get(0).text());
        assertEquals("درباره حرف علی می‌گم.", ReplySegments.plain("[[REPLY]]درباره حرف علی می‌گم."));
    }
}
