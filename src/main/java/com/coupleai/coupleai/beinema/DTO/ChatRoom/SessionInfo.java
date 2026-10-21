package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Clock and break state of the current counseling session of a room.
 *
 * A session is open (active) from the moment the first partner enters it. Its 45
 * minutes start when the other partner enters too, or after a short wait. When
 * they are over, a break is offered; whoever goes on may talk for up to 15 more
 * minutes, then the session ends. The next one may open after a short break, and
 * a room holds at most a few sessions in 24 hours.
 */
@Getter
@Builder
public class SessionInfo {

    /** The current session, or (between sessions) the last one. */
    private Integer number;

    /** False between two sessions. */
    private Boolean active;

    private Long durationSeconds;

    /** Null while the session waits for the other partner. */
    private Long startedAt;

    /** End of the 45 minutes. */
    private Long endsAt;

    /** End of the 15 extra minutes: the session closes at this moment at the latest. */
    private Long overtimeEndsAt;

    /** Server clock, so every phone counts down against the same time. */
    private Long serverNow;

    /** The break was offered (the 45 minutes are over); each partner answers for themselves. */
    private Boolean breakProposed;

    private List<Vote> votes;

    private Boolean hasSummary;

    /** When the last session ended (between sessions only). */
    private Long endedAt;

    /** Between sessions: the next one cannot start before this moment (the short break). */
    private Long nextAvailableAt;

    /** While someone is in and the other is not: when the waiting time runs out. */
    private Long waitEndsAt;

    /** The first session waits while the other partner, already here, reads and accepts the rules. */
    private Boolean waitingForConsent;

    /** Who is in the current session. */
    private List<Ready> ready;

    /** Between sessions: the room held the most sessions allowed in 24 hours. */
    private Boolean limitReached;

    /** When the next session may open because of that limit. */
    private Long limitUntil;

    /** The most sessions a room may hold in 24 hours. */
    private Integer sessionLimit;

    /** Sessions of this room that ended with a conversation. */
    private Integer completedSessions;

    /** The rules of the clock, for the texts on the cards. */
    private Integer waitMinutes;
    private Integer overtimeMinutes;
    private Integer breakMinutes;

    @Getter
    @Builder
    public static class Ready {
        private Long userId;
        private String name;
        /** In the current session. */
        private Boolean ready;
    }

    @Getter
    @Builder
    public static class Vote {
        private Long userId;
        private String name;
        /** Null = has not answered yet; true = takes the break; false = goes on. */
        private Boolean agree;
    }
}
