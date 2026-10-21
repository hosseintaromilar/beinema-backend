package com.coupleai.coupleai.beinema.governance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markers that tie chat messages to a structured clarifying question. The
 * counselor message that shows a question starts with [[QUESTION|ID=...]], the
 * partner's answer with [[ANSWER|Q=...]]. The question row stays the only
 * source of the question's state; the markers only carry its public id.
 */
public final class QuestionMarkers {

    private static final Pattern QUESTION = Pattern.compile("\\[\\[QUESTION\\|ID=([A-Za-z0-9-]{8,40})\\]\\]");
    private static final Pattern ANSWER = Pattern.compile("\\[\\[ANSWER\\|Q=([A-Za-z0-9-]{8,40})\\]\\]");

    private QuestionMarkers() {
    }

    public static String questionContent(String publicId, String questionText) {
        return "[[QUESTION|ID=" + publicId + "]]\n" + (questionText == null ? "" : questionText.trim());
    }

    public static String answerContent(String publicId, String label, String freeText) {
        StringBuilder content = new StringBuilder("[[ANSWER|Q=").append(publicId).append("]]\n")
                .append(label == null ? "" : label.trim());
        if (freeText != null && !freeText.isBlank()) {
            content.append('\n').append(freeText.trim());
        }
        return content.toString();
    }

    /** The public id of the question a counselor message shows, or null. */
    public static String questionId(String content) {
        return find(QUESTION, content);
    }

    /** The public id of the question a partner message answers, or null. */
    public static String answerTo(String content) {
        return find(ANSWER, content);
    }

    public static String strip(String content) {
        if (content == null || content.isEmpty()) {
            return content == null ? "" : content;
        }
        String withoutQuestion = QUESTION.matcher(content).replaceAll("");
        return ANSWER.matcher(withoutQuestion).replaceAll("").trim();
    }

    private static String find(Pattern pattern, String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }
}
