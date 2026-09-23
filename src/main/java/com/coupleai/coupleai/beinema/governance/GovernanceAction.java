package com.coupleai.coupleai.beinema.governance;

public enum GovernanceAction {
    NONE,
    CAUTION,
    FIRM_INTERVENTION,
    PAUSE_USER,
    PAUSE_BOTH,
    END_SESSION;

    public boolean changesParticipation() {
        return this == PAUSE_USER || this == PAUSE_BOTH || this == END_SESSION;
    }

    public static GovernanceAction fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return GovernanceAction.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
