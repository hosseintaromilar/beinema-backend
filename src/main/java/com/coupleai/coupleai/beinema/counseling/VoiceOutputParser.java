package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits the voice's reply into the ordinary text (with its BREAK, STICKER and
 * REPLY markers, delivered as before) and an optional structured question block:
 * <pre>
 * [[QUESTION]]
 * question sentence
 * [[OPT|i45]] option
 * [[/QUESTION]]
 * </pre>
 */
public final class VoiceOutputParser {

    public static final String ERROR_MARKER = "[[ERROR|";
    private static final String OPEN = "[[QUESTION]]";
    private static final String CLOSE = "[[/QUESTION]]";
    private static final Pattern OPTION = Pattern.compile("^\\s*\\[\\[OPT\\|([A-Za-z0-9]{1,12})\\]\\]\\s*(.+?)\\s*$");

    public record Result(String text, String questionText, Map<String, String> options, boolean questionBlock,
                         String error, List<String> notes) {
        public boolean failed() {
            return error != null;
        }
    }

    private VoiceOutputParser() {
    }

    public static Result parse(String raw) {
        List<String> notes = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new Result("", null, Map.of(), false, "EMPTY", notes);
        }
        if (raw.contains(ERROR_MARKER)) {
            int start = raw.indexOf(ERROR_MARKER) + ERROR_MARKER.length();
            int end = raw.indexOf("]]", start);
            return new Result("", null, Map.of(), false, end > start ? raw.substring(start, end) : "VOICE_FAILED", notes);
        }
        // a stray governance line or director leak never reaches the couple
        String text = new GovernancePacketParser().strip(raw.replace("\r", ""));
        int open = text.indexOf(OPEN);
        if (open < 0) {
            return new Result(text.trim(), null, Map.of(), false, null, notes);
        }
        String before = text.substring(0, open).trim();
        int close = text.indexOf(CLOSE, open);
        String block = close < 0 ? text.substring(open + OPEN.length()) : text.substring(open + OPEN.length(), close);
        if (close >= 0 && !text.substring(close + CLOSE.length()).isBlank()) {
            notes.add("voice: text after the question block was dropped");
        }
        StringBuilder question = new StringBuilder();
        Map<String, String> options = new LinkedHashMap<>();
        for (String line : block.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            Matcher option = OPTION.matcher(line);
            if (option.matches()) {
                options.putIfAbsent(option.group(1), option.group(2));
            } else if (options.isEmpty()) {
                question.append(question.length() == 0 ? "" : " ").append(line.trim());
            } else {
                notes.add("voice: a line inside the block after the options was ignored");
            }
        }
        return new Result(before, question.toString().trim(), options, true, null, notes);
    }
}
