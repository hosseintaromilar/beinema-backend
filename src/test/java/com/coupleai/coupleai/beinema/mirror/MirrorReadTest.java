package com.coupleai.coupleai.beinema.mirror;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MirrorReadTest {

    private final Instant built = Instant.parse("2026-11-16T10:00:00Z");

    @Test
    void openedAfterItWasWrittenCountsAsSeen() {
        MirrorRead read = new MirrorRead();
        read.setSeenAt(built.plusSeconds(60));
        assertThat(read.seen(built)).isTrue();
        assertThat(read.handled(built)).isTrue();
    }

    @Test
    void aMirrorWrittenAgainAfterALaterSessionIsNewAgain() {
        MirrorRead read = new MirrorRead();
        read.setSeenAt(built);
        read.setEmailedAt(built.plusSeconds(10));
        Instant rewritten = built.plusSeconds(3600);
        assertThat(read.seen(rewritten)).isFalse();
        assertThat(read.handled(rewritten)).isFalse();
    }

    @Test
    void emailedButNotOpenedIsHandledButStillShownAsReady() {
        MirrorRead read = new MirrorRead();
        read.setEmailedAt(built.plusSeconds(86_400));
        assertThat(read.handled(built)).isTrue();
        assertThat(read.seen(built)).isFalse();
    }
}
