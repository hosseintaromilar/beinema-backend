package com.coupleai.coupleai.beinema.governance;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GovernancePacketParser {

    public static final String MARKER_PREFIX = "[[GOV|";
    public static final String MARKER_SUFFIX = "]]";

    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[GOV\\|([^\\]]*)\\]\\]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> SESSION_ACTIONS = SessionPacketParser.ACTIONS;

    public GovernanceDecision parse(String rawReply) {
        if (rawReply == null || rawReply.isBlank()) {
            return GovernanceDecision.none("");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        String visible = rawReply;
        boolean malformed = false;
        boolean sawMarker = false;

        Matcher marker = MARKER.matcher(rawReply);
        if (marker.find()) {
            sawMarker = true;
            Map<String, String> markerFields = parsePipePacket(marker.group(1));
            fields.putAll(markerFields);
            visible = (rawReply.substring(0, marker.start()) + rawReply.substring(marker.end())).trim();
            if (marker.group(1) == null || marker.group(1).isBlank() || markerFields.isEmpty()) {
                malformed = true;
            }
        }

        String trimmed = rawReply.trim();
        boolean directorPacket = trimmed.startsWith("ACTION=") && trimmed.contains("|");
        if (directorPacket) {
            fields.putAll(parsePipePacket(firstLine(trimmed)));
            if (!sawMarker) {
                visible = "";
            }
        }

        GovernanceAction governanceAction = resolveGovernanceAction(fields, sawMarker);
        if (sawMarker && fields.containsKey("ACTION") && !fields.containsKey("GOV")) {
            String token = fields.get("ACTION");
            GovernanceAction parsed = GovernanceAction.fromToken(token);
            if (parsed == GovernanceAction.NONE
                    && token != null
                    && !token.isBlank()
                    && !"NONE".equalsIgnoreCase(token)
                    && !SESSION_ACTIONS.contains(token.toUpperCase(Locale.ROOT))) {
                malformed = true;
            }
        }
        if (fields.containsKey("GOV")) {
            String govToken = fields.get("GOV");
            if (govToken != null
                    && !govToken.isBlank()
                    && !"NONE".equalsIgnoreCase(govToken)
                    && GovernanceAction.fromToken(govToken) == GovernanceAction.NONE) {
                malformed = true;
            }
        }

        String sessionAction = first(fields, "SESSION", "SESSIONACTION");
        if (sessionAction == null && directorPacket) {
            sessionAction = fields.get("ACTION");
        }

        GovernanceTarget target = GovernanceTarget.fromToken(first(fields, "TARGET"));
        Integer duration = parseDuration(first(fields, "DURATION", "DURATIONSECONDS"));
        String reason = first(fields, "REASON", "BRIEF");
        visible = SessionPacketParser.strip(stripDirectorLeak(visible));

        return new GovernanceDecision(
                sessionAction,
                governanceAction,
                target,
                duration,
                reason,
                visible,
                malformed
        );
    }

    public String strip(String rawReply) {
        return parse(rawReply).visibleText();
    }

    static Map<String, String> parsePipePacket(String packet) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (packet == null || packet.isBlank()) {
            return fields;
        }
        for (String part : packet.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim().toUpperCase(Locale.ROOT);
            String value = part.substring(eq + 1).trim();
            fields.put(key, value);
        }
        return fields;
    }

    private static GovernanceAction inferPauseFromSessionMarker(Map<String, String> fields) {
        Integer duration = parseDuration(first(fields, "DURATION", "DURATIONSECONDS"));
        if (duration == null || duration < 30) {
            return GovernanceAction.NONE;
        }
        GovernanceTarget target = GovernanceTarget.fromToken(first(fields, "TARGET"));
        if (target == GovernanceTarget.BOTH) {
            return GovernanceAction.PAUSE_BOTH;
        }
        if (target == GovernanceTarget.OWNER || target == GovernanceTarget.MEMBER) {
            return GovernanceAction.PAUSE_USER;
        }
        return GovernanceAction.NONE;
    }

    private static GovernanceAction resolveGovernanceAction(
            Map<String, String> fields,
            boolean sawMarker
    ) {
        if (fields.containsKey("GOV")) {
            return GovernanceAction.fromToken(fields.get("GOV"));
        }
        if (fields.containsKey("GOVERNANCE")) {
            return GovernanceAction.fromToken(fields.get("GOVERNANCE"));
        }
        if (sawMarker) {
            String token = fields.get("ACTION");
            if (token != null && SESSION_ACTIONS.contains(token.toUpperCase(Locale.ROOT))
                    && !"END_SESSION".equalsIgnoreCase(token)) {
                return inferPauseFromSessionMarker(fields);
            }
            return GovernanceAction.fromToken(token);
        }
        return GovernanceAction.NONE;
    }

    private static String first(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Integer parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl).trim();
    }

    static String stripDirectorLeak(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("ACTION=") && trimmed.contains("|PHASE=")) {
            return "";
        }
        return MARKER.matcher(text).replaceAll("").trim();
    }
}
