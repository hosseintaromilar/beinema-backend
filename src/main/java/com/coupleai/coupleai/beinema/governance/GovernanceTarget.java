package com.coupleai.coupleai.beinema.governance;

public enum GovernanceTarget {
    NONE,
    OWNER,
    MEMBER,
    BOTH;

    public static GovernanceTarget fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "OWNER", "PARTNER_A", "A" -> OWNER;
            case "MEMBER", "PARTNER_B", "B" -> MEMBER;
            case "BOTH", "ALL" -> BOTH;
            default -> NONE;
        };
    }
}
