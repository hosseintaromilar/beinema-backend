package com.coupleai.coupleai.beinema.governance;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SessionPacketParser {

    public static final Set<String> ACTIONS = Set.of(
            "ASK", "REFLECT", "CLARIFY", "INTERVENE", "REDIRECT",
            "SUMMARIZE", "SAFETY", "LISTENING", "LISTEN", "WAIT", "END_SESSION",
            "EXPLORE", "DEEPEN", "CHALLENGE", "VALIDATE", "EVALUATE",
            "PLAN", "PAUSE", "SWITCH"
    );

    private static final Set<String> DOMAINS = Set.of("RELATION", "PERSON", "CHANGE");
    private static final Set<String> FOCUS = Set.of("A", "B", "BOTH");
    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[(PKT|DIR|EPI)\\|([^\\]]*)\\]\\]",
            Pattern.CASE_INSENSITIVE
    );

    private SessionPacketParser() {
    }

    public static SessionPacket director(String raw) {
        return read(raw, "DIR");
    }

    public static SessionPacket execution(String raw) {
        return read(raw, "PKT");
    }

    public static String strip(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return MARKER.matcher(raw).replaceAll("").trim();
    }

    private static SessionPacket read(String raw, String kind) {
        if (raw == null || raw.isBlank()) {
            return SessionPacket.absent();
        }
        Matcher matcher = MARKER.matcher(raw);
        while (matcher.find()) {
            if (kind.equalsIgnoreCase(matcher.group(1))) {
                return interpret(GovernancePacketParser.parsePipePacket(matcher.group(2)));
            }
        }
        return SessionPacket.absent();
    }

    static SessionPacket interpret(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return SessionPacket.rejected("incomplete");
        }
        String domain = upper(fields.get("DOMAIN"));
        String focus = upper(first(fields, "FOCUS"));
        if (focus == null) {
            String target = upper(fields.get("TARGET"));
            if (target != null && FOCUS.contains(target)) {
                focus = target;
            }
        }
        String move = upper(first(fields, "MOVE", "ACTION"));
        String confidence = upper(fields.get("CONF"));
        String claimKind = upper(fields.get("KIND"));
        if (domain == null || focus == null || move == null) {
            return SessionPacket.rejected("incomplete");
        }
        if (!DOMAINS.contains(domain) || !FOCUS.contains(focus) || !ACTIONS.contains(move)) {
            return SessionPacket.rejected("invalid-target");
        }
        return new SessionPacket(
                true,
                true,
                upper(fields.get("STAGE")),
                domain,
                focus,
                move,
                fields.get("INTENT"),
                upper(fields.get("DEPTH")),
                confidence,
                fields.get("NEXT"),
                upper(first(fields, "GOV")),
                claimKind,
                null,
                blankToNull(fields.get("FACT"))
        );
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

    private static String upper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
