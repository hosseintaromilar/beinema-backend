package com.coupleai.coupleai.beinema.governance;

public class GovStreamBuffer {

    private final StringBuilder pending = new StringBuilder();
    private final GovernancePacketParser parser = new GovernancePacketParser();
    private boolean headerResolved;
    private int emittedLength;

    public String accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        pending.append(chunk);
        String raw = pending.toString();
        int openMarker = unclosedMarker(raw);
        String stable = openMarker < 0 ? raw : raw.substring(0, openMarker);
        String visible = parser.strip(stable);
        if (!headerResolved) {
            boolean directorOpen = raw.trim().startsWith("ACTION=")
                    && raw.contains("|")
                    && !raw.contains("\n")
                    && raw.length() < 500;
            if (openMarker >= 0 || directorOpen) {
                return "";
            }
            headerResolved = true;
        }
        if (visible.length() <= emittedLength) {
            return "";
        }
        String delta = visible.substring(emittedLength);
        emittedLength = visible.length();
        return delta;
    }

    public String visibleText() {
        return parser.strip(pending.toString());
    }

    public GovernanceDecision finish() {
        return parser.parse(pending.toString());
    }

    public String raw() {
        return pending.toString();
    }

    private static int unclosedMarker(String raw) {
        int start = raw.lastIndexOf("[[");
        if (start < 0) {
            return -1;
        }
        return raw.indexOf("]]", start) < 0 ? start : -1;
    }
}
