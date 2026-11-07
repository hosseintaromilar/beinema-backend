package com.coupleai.coupleai.beinema.governance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A card inside a companion's message (a test, its result, a lesson). The message carries
 * only the card's kind and id, with a short line of text for anything that cannot draw it.
 */
public final class KitMarkers {

    public static final String TEST = "TEST";
    public static final String RESULT = "RESULT";
    public static final String LESSON = "LESSON";
    public static final String END_CHECK = "ENDCHECK";
    public static final String MODE = "MODE";
    /** The financial companion's cards: the risk quiz, the financial picture, a market report, a plan. */
    public static final String RISK = "RISK";
    public static final String PICTURE = "PICTURE";
    public static final String MARKET = "MARKET";
    public static final String PLAN = "PLAN";

    private static final Pattern CARD = Pattern.compile("\\[\\[KIT\\|(TEST|RESULT|LESSON|ENDCHECK|MODE|RISK|PICTURE|MARKET|PLAN)=([A-Za-z0-9-]{3,40})\\]\\]\\s*");

    private KitMarkers() {
    }

    public static String content(String kind, String ref, String text) {
        return "[[KIT|" + kind + "=" + ref + "]]\n" + (text == null ? "" : text.trim());
    }

    public static String kind(String content) {
        Matcher matcher = content == null ? null : CARD.matcher(content);
        return matcher != null && matcher.find() ? matcher.group(1) : null;
    }

    public static String ref(String content) {
        Matcher matcher = content == null ? null : CARD.matcher(content);
        return matcher != null && matcher.find() ? matcher.group(2) : null;
    }

    public static String strip(String content) {
        return content == null ? null : CARD.matcher(content).replaceAll("");
    }
}
