package com.coupleai.coupleai.beinema.counseling;

import java.util.regex.Pattern;

/**
 * Normalizes Persian text so the same words match however they were typed:
 * Arabic yeh and kaf, zero-width non-joiners, diacritics, the space after the
 * prefix «می», and repeated spaces. Used for quote checks and word lists only;
 * the stored text is never changed.
 */
public final class PersianText {

    private static final Pattern DIACRITICS = Pattern.compile("[\\u064B-\\u0652\\u0670\\u0640]");
    private static final Pattern MI_PREFIX = Pattern.compile("(?<=^|\\s)(ن?می)\\s+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}«»،؛؟!…]+");

    private PersianText() {
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String value = text
                .replace('ي', 'ی')
                .replace('ى', 'ی')
                .replace('ك', 'ک')
                .replace('ة', 'ه')
                .replace("‌", "")
                .replace("‏", "")
                .replace("‎", "")
                .toLowerCase(java.util.Locale.ROOT);
        value = DIACRITICS.matcher(value).replaceAll("");
        value = MI_PREFIX.matcher(value).replaceAll("$1");
        return SPACES.matcher(value).replaceAll(" ").trim();
    }

    /** Normalized and without punctuation, for loose quote comparison. */
    public static String loose(String text) {
        String value = PUNCTUATION.matcher(normalize(text)).replaceAll(" ");
        return SPACES.matcher(value).replaceAll(" ").trim();
    }

    public static boolean containsAny(String normalizedText, String... normalizedPhrases) {
        for (String phrase : normalizedPhrases) {
            if (normalizedText.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    public static int words(String text) {
        String value = normalize(text);
        return value.isEmpty() ? 0 : value.split(" ").length;
    }

    public static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String value = text.replace('\r', ' ').trim();
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
