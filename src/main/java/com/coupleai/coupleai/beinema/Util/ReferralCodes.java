package com.coupleai.coupleai.beinema.Util;

import java.security.SecureRandom;
import java.util.Locale;

public final class ReferralCodes {

    public static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int LENGTH = 8;

    private ReferralCodes() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) {
            return "";
        }
        for (int index = 0; index < code.length(); index++) {
            if (ALPHABET.indexOf(code.charAt(index)) < 0) {
                return "";
            }
        }
        return code;
    }

    public static String generate(SecureRandom random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int index = 0; index < LENGTH; index++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
