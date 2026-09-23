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
        String visible = parser.strip(pending.toString());
        if (!headerResolved) {
            String raw = pending.toString();
            boolean markerOpen = raw.contains(GovernancePacketParser.MARKER_PREFIX)
                    && !raw.contains(GovernancePacketParser.MARKER_SUFFIX);
            boolean directorOpen = raw.trim().startsWith("ACTION=")
                    && raw.contains("|")
                    && !raw.contains("\n")
                    && raw.length() < 400;
            if (markerOpen || directorOpen) {
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
}
