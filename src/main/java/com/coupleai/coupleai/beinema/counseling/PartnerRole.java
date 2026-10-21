package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Enum.ParticipantRole;

/** The two stable names the counselor uses for the partners: OWNER and MEMBER. */
public final class PartnerRole {

    public static final String OWNER = "OWNER";
    public static final String MEMBER = "MEMBER";
    public static final String BOTH = "BOTH";

    private PartnerRole() {
    }

    public static String of(ParticipantRole role) {
        if (role == ParticipantRole.OWNER || role == ParticipantRole.PARTNER_A) {
            return OWNER;
        }
        return MEMBER;
    }

    public static boolean isPartner(String role) {
        return OWNER.equals(role) || MEMBER.equals(role);
    }

    public static String other(String role) {
        return OWNER.equals(role) ? MEMBER : OWNER;
    }
}
