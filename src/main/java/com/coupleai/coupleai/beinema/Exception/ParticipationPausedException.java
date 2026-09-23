package com.coupleai.coupleai.beinema.Exception;

import java.time.Instant;

public class ParticipationPausedException extends RuntimeException {

    private final Instant pausedUntil;
    private final long remainingSeconds;

    public ParticipationPausedException(String message, Instant pausedUntil, long remainingSeconds) {
        super(message);
        this.pausedUntil = pausedUntil;
        this.remainingSeconds = remainingSeconds;
    }

    public Instant getPausedUntil() {
        return pausedUntil;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}
