package com.coupleai.coupleai.beinema.governance;

public final class EntryGreeting {

    public static final long REPEAT_WINDOW_MILLIS = 20_000L;

    private EntryGreeting() {
    }

    public static String sessionReady(String names) {
        String present = names == null || names.isBlank() ? "" : names.trim();
        return "[SYSTEM | SESSION]\n"
                + "EVENT=SESSION_READY\n"
                + "PARTICIPANTS: " + present + "\n";
    }

    public static String participantJoined(String arrivedName, boolean consented, String transcript) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[SYSTEM | SESSION]\n");
        prompt.append("EVENT=PARTICIPANT_JOINED\n");
        prompt.append("ARRIVED: ").append(arrivedName == null ? "" : arrivedName).append('\n');
        prompt.append("CONSENTED: ").append(consented).append('\n');
        prompt.append("SESSION_ALREADY_ACTIVE=true\n");
        prompt.append("PRESERVE_STAGE=true\n");
        prompt.append("PRESERVE_TARGET=true\n");
        if (transcript != null && !transcript.isBlank()) {
            prompt.append("TRANSCRIPT:\n").append(transcript);
        }
        return prompt.toString();
    }

    public static String participantConsented(String arrivedName) {
        return "[SYSTEM | SESSION]\n"
                + "EVENT=PARTICIPANT_CONSENTED\n"
                + "ARRIVED: " + (arrivedName == null ? "" : arrivedName) + "\n"
                + "SESSION_ALREADY_ACTIVE=true\n"
                + "PRESERVE_STAGE=true\n"
                + "PRESERVE_TARGET=true\n";
    }

    public static String instruction(
            String arrivedName,
            String others,
            boolean fresh,
            boolean ended,
            boolean consented,
            boolean partnerPending,
            boolean held,
            String transcript
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[SYSTEM | SESSION]\n");
        prompt.append("EVENT=PARTICIPANT_ENTERED\n");
        prompt.append("ARRIVED: ").append(arrivedName == null ? "" : arrivedName).append('\n');
        prompt.append("OTHERS: ")
                .append(others == null || others.isBlank() ? "NONE" : others)
                .append('\n');
        prompt.append("FRESH: ").append(fresh).append('\n');
        prompt.append("ENDED: ").append(ended).append('\n');
        prompt.append("CONSENTED: ").append(consented).append('\n');
        prompt.append("PARTNER_PENDING: ").append(partnerPending).append('\n');
        prompt.append("HELD: ").append(held).append('\n');
        if (transcript != null && !transcript.isBlank()) {
            prompt.append("TRANSCRIPT:\n").append(transcript);
        }
        return prompt.toString();
    }
}
