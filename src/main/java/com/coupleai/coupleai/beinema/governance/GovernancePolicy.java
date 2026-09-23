package com.coupleai.coupleai.beinema.governance;

import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GovernancePolicy {

    public record Command(
            GovernanceAction action,
            GovernanceTarget target,
            List<Long> userIds,
            Instant pauseUntil,
            boolean endSession,
            String rejectReason
    ) {
        public boolean shouldEnforce() {
            return rejectReason == null
                    && action != null
                    && action.changesParticipation();
        }
    }

    private final GovernanceProperties properties;
    private final Clock clock;

    public GovernancePolicy(GovernanceProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    public GovernancePolicy(GovernanceProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Command normalize(
            GovernanceDecision decision,
            List<ChatRoomParticipant> participants,
            boolean sessionActive
    ) {
        if (decision == null || decision.malformed()) {
            return ignored("malformed");
        }
        if (!sessionActive) {
            return ignored("session_inactive");
        }
        GovernanceAction action = decision.governanceAction() == null
                ? GovernanceAction.NONE
                : decision.governanceAction();
        if (!action.changesParticipation()) {
            return new Command(action, GovernanceTarget.NONE, List.of(), null, false, null);
        }

        if (action == GovernanceAction.END_SESSION) {
            return new Command(action, GovernanceTarget.BOTH, List.of(), null, true, null);
        }

        GovernanceTarget target = decision.target() == null ? GovernanceTarget.NONE : decision.target();
        if (action == GovernanceAction.PAUSE_BOTH) {
            target = GovernanceTarget.BOTH;
        }
        if (action == GovernanceAction.PAUSE_USER && (target == GovernanceTarget.NONE || target == GovernanceTarget.BOTH)) {
            return ignored("target_invalid");
        }

        List<Long> userIds = resolveTargets(target, participants);
        if (userIds.isEmpty()) {
            return ignored("target_not_in_session");
        }
        if (action == GovernanceAction.PAUSE_USER && userIds.size() != 1) {
            return ignored("target_invalid");
        }

        int seconds = clampDuration(decision.durationSeconds());
        Instant until = clock.instant().plusSeconds(seconds);
        return new Command(action, target, userIds, until, false, null);
    }

    public Instant mergePause(Instant existing, Instant incoming) {
        if (incoming == null) {
            return existing;
        }
        if (existing == null || incoming.isAfter(existing)) {
            return incoming;
        }
        return existing;
    }

    public boolean isActive(Instant pauseUntil) {
        return pauseUntil != null && pauseUntil.isAfter(clock.instant());
    }

    public long remainingSeconds(Instant pauseUntil) {
        if (!isActive(pauseUntil)) {
            return 0;
        }
        return Math.max(1, Duration.between(clock.instant(), pauseUntil).getSeconds());
    }

    int clampDuration(Integer requested) {
        int min = Math.max(1, properties.getPauseMinSeconds());
        int max = Math.max(min, properties.getPauseMaxSeconds());
        int fallback = Math.min(max, Math.max(min, properties.getPauseDefaultSeconds()));
        if (requested == null || requested <= 0) {
            return fallback;
        }
        return Math.min(max, Math.max(min, requested));
    }

    List<Long> resolveTargets(GovernanceTarget target, List<ChatRoomParticipant> participants) {
        if (participants == null || participants.isEmpty() || target == null || target == GovernanceTarget.NONE) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (ChatRoomParticipant participant : participants) {
            if (participant.getUser() == null || participant.getUser().getId() == null) {
                continue;
            }
            if (matches(target, participant.getRole())) {
                ids.add(participant.getUser().getId());
            }
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static boolean matches(GovernanceTarget target, ParticipantRole role) {
        if (target == GovernanceTarget.BOTH) {
            return true;
        }
        if (role == null) {
            return false;
        }
        if (target == GovernanceTarget.OWNER) {
            return role == ParticipantRole.OWNER || role == ParticipantRole.PARTNER_A;
        }
        if (target == GovernanceTarget.MEMBER) {
            return role == ParticipantRole.MEMBER || role == ParticipantRole.PARTNER_B;
        }
        return false;
    }

    private static Command ignored(String reason) {
        return new Command(GovernanceAction.NONE, GovernanceTarget.NONE, List.of(), null, false, reason);
    }
}
