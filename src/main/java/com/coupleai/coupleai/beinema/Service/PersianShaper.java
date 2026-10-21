package com.coupleai.coupleai.beinema.Service;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns Persian/Arabic text into the joined letter shapes (Arabic Presentation Forms) that a PDF
 * font without its own shaping engine can draw.
 *
 * <p>ICU's own shaper does not know the Persian-only letters (پ چ ژ ک گ ی) and ignores them when
 * it decides how their neighbours join, so a word such as «گفتگو» came out with broken joins. Here
 * every letter gets its form from its real neighbours; the other letters borrow their forms from ICU.
 */
final class PersianShaper {

    private static final String DUAL = "بپتثجچحخسشصضطظعغفقکگلمنهیئكيىـ";
    private static final String RIGHT = "آأإاةدذرزژوؤ";
    private static final String MARKS = "ًٌٍَُِّْٰٕٓٔ";
    private static final char ZWJ = '‍';

    // isolated, final, initial, medial
    private static final Map<Character, int[]> PERSIAN = Map.of(
            'پ', new int[]{0xFB56, 0xFB57, 0xFB58, 0xFB59},
            'چ', new int[]{0xFB7A, 0xFB7B, 0xFB7C, 0xFB7D},
            'ژ', new int[]{0xFB8A, 0xFB8B, 0, 0},
            'ک', new int[]{0xFB8E, 0xFB8F, 0xFB90, 0xFB91},
            'گ', new int[]{0xFB92, 0xFB93, 0xFB94, 0xFB95},
            'ی', new int[]{0xFBFC, 0xFBFD, 0xFBFE, 0xFBFF});

    private static final Map<Character, int[]> CACHE = new HashMap<>();

    private PersianShaper() {
    }

    static String shape(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (!isLetter(c)) {
                out.append(c);
                continue;
            }
            boolean joinsPrevious = joinsForward(previousLetter(text, i));
            // lam + alef is drawn as one ligature
            if (c == 'ل') {
                int next = nextIndex(text, i);
                if (next >= 0 && "اآأإ".indexOf(text.charAt(next)) >= 0) {
                    out.append(ligature(c, text.charAt(next), joinsPrevious));
                    for (int k = i + 1; k < next; k++) {
                        out.append(text.charAt(k));
                    }
                    i = next;
                    continue;
                }
            }
            boolean joinsNext = DUAL.indexOf(c) >= 0 && joinsBackward(nextLetter(text, i));
            int index = joinsPrevious ? (joinsNext ? 3 : 1) : (joinsNext ? 2 : 0);
            out.append((char) form(c, index));
        }
        return out.toString();
    }

    private static boolean isLetter(char c) {
        return DUAL.indexOf(c) >= 0 || RIGHT.indexOf(c) >= 0;
    }

    /** Can a letter that comes before connect to the next one? */
    private static boolean joinsForward(char previous) {
        return previous != 0 && DUAL.indexOf(previous) >= 0;
    }

    /** Can a letter that comes after connect to this one? */
    private static boolean joinsBackward(char next) {
        return next != 0 && (DUAL.indexOf(next) >= 0 || RIGHT.indexOf(next) >= 0);
    }

    private static char previousLetter(String text, int i) {
        for (int k = i - 1; k >= 0; k--) {
            char c = text.charAt(k);
            if (MARKS.indexOf(c) >= 0) {
                continue;
            }
            return isLetter(c) ? c : 0;
        }
        return 0;
    }

    private static int nextIndex(String text, int i) {
        for (int k = i + 1; k < text.length(); k++) {
            char c = text.charAt(k);
            if (MARKS.indexOf(c) >= 0) {
                continue;
            }
            return isLetter(c) ? k : -1;
        }
        return -1;
    }

    private static char nextLetter(String text, int i) {
        int k = nextIndex(text, i);
        return k < 0 ? 0 : text.charAt(k);
    }

    private static int form(char c, int index) {
        int[] persian = PERSIAN.get(c);
        if (persian != null) {
            // ژ never joins forward: its initial/medial shapes are the isolated/final ones
            int value = persian[index];
            return value != 0 ? value : persian[index == 2 ? 0 : 1];
        }
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(c, PersianShaper::icuForms)[index];
        }
    }

    private static int[] icuForms(char c) {
        String letter = String.valueOf(c);
        return new int[]{
                icu(letter), icu(ZWJ + letter), icu(letter + ZWJ), icu(ZWJ + letter + ZWJ)};
    }

    private static char ligature(char lam, char alef, boolean joinsPrevious) {
        String pair = "" + lam + alef;
        return (char) icu(joinsPrevious ? ZWJ + pair : pair);
    }

    private static int icu(String joined) {
        try {
            String shaped = new ArabicShaping(ArabicShaping.LETTERS_SHAPE).shape(joined);
            for (int i = shaped.length() - 1; i >= 0; i--) {
                if (shaped.charAt(i) != ZWJ && shaped.charAt(i) != ' ') {
                    return shaped.charAt(i);
                }
            }
        } catch (ArabicShapingException ignored) {
            // fall through to the plain letter
        }
        return joined.charAt(joined.length() - 1);
    }
}
