package com.coupleai.coupleai.beinema.governance;

public record SessionPacket(
        boolean present,
        boolean acceptable,
        String stage,
        String domain,
        String target,
        String action,
        String intent,
        String depth,
        String confidence,
        String nextCondition,
        String governance,
        String kind,
        String rejection,
        String claim
) {
    public static SessionPacket absent() {
        return new SessionPacket(
                false,
                true,
                null, null, null, null, null, null,
                null, null, null, null, null, null
        );
    }

    public static SessionPacket rejected(String reason) {
        return new SessionPacket(
                true,
                false,
                null, null, null, null, null, null,
                null, null, null, null, reason, null
        );
    }
}
