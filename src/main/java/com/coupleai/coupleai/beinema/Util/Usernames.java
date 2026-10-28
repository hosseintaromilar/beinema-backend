package com.coupleai.coupleai.beinema.Util;

import java.util.Locale;

/**
 * Sign-in names: English letters, digits, dot and underscore, 3 to 30 long,
 * starting with a letter. Kept in lower case, so «Sara» and «sara» are one name.
 */
public final class Usernames {

    private static final String RULE =
            "نام کاربری باید ۳ تا ۳۰ نویسه باشد، با حرف انگلیسی شروع شود و فقط حرف انگلیسی، عدد، نقطه یا _ داشته باشد.";

    private Usernames() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("نام کاربری را وارد کن.");
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z][a-z0-9._]{2,29}")) {
            throw new IllegalArgumentException(RULE);
        }
        return name;
    }

    /** What someone typed in the sign-in box, without judging it (wrong input is just a wrong sign-in). */
    public static String lenient(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
