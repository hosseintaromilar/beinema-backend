package com.coupleai.coupleai.beinema.governance;

public record GovernanceDecision(
        String sessionAction,
        GovernanceAction governanceAction,
        GovernanceTarget target,
        Integer durationSeconds,
        String reason,
        String visibleText,
        boolean malformed
) {
    public static GovernanceDecision none(String visibleText) {
        return new GovernanceDecision(
                null,
                GovernanceAction.NONE,
                GovernanceTarget.NONE,
                0,
                null,
                visibleText,
                false
        );
    }
}
