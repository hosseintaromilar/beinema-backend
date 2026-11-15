package com.coupleai.coupleai.beinema.Util;

/**
 * How long one chat message may be: long enough for a heartfelt message (about 250 words), too short to
 * paste a whole conversation from another account into a new one. The composer counts the same number.
 */
public final class MessageLimits {

    public static final int MAX_CHARS = 1500;

    public static final String TOO_LONG = "پیام حداکثر ۱۵۰۰ حرف می‌تواند باشد؛ اگر حرفت بیشتر است، در چند پیام بفرست.";

    private MessageLimits() {
    }

    public static boolean tooLong(String text) {
        return text != null && text.strip().length() > MAX_CHARS;
    }
}
