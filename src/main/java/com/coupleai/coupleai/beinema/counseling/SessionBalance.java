package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.IntentStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * «Takes no one's side», measured: in this session, how much each partner wrote, how many of the
 * companion's turns spoke to each and asked each, and how many of each one's readings they confirmed.
 * It only informs: one line for the director, with a gentle word when the attention clearly leans to
 * one side. The director decides; a painful moment or a safety concern may rightly hold the attention.
 */
public final class SessionBalance {

    /** Too few turns say nothing about balance. */
    static final int MIN_TURNS = 4;
    /** One partner had this share of the companion's turns or more: worth a word. */
    static final double LEAN = 0.65;

    private SessionBalance() {
    }

    record Count(int words, int addressed, int asked, int confirmed) {
    }

    /**
     * The line for the director, or "" when there is no couple in the room or too little has happened.
     * sessionMessages: this session's messages; turns: this session's decisions of the companion.
     */
    static String line(Set<String> partnersHere, Map<String, String> names, List<Message> sessionMessages,
                       Map<Long, String> roleByMessageId, List<DirectorDecisionRecord> turns, List<ClientIntent> intents) {
        if (partnersHere == null || !partnersHere.containsAll(List.of("OWNER", "MEMBER"))) {
            return "";
        }
        Map<String, Count> counts = counts(sessionMessages, roleByMessageId, turns, intents);
        int spoken = counts.get("OWNER").addressed() + counts.get("MEMBER").addressed();
        if (spoken < MIN_TURNS) {
            return "";
        }
        StringBuilder out = new StringBuilder("balance this session: ");
        for (String role : List.of("OWNER", "MEMBER")) {
            Count count = counts.get(role);
            out.append(role).append(" wrote ").append(count.words()).append(" words, was spoken to in ")
                    .append(count.addressed()).append(" turns, asked ").append(count.asked())
                    .append(", readings confirmed ").append(count.confirmed()).append(role.equals("OWNER") ? "; " : "\n");
        }
        String quiet = leaning(counts);
        if (!quiet.isEmpty()) {
            String name = names == null ? "" : names.getOrDefault(quiet, "");
            out.append("the attention leans away from ").append(quiet).append(name.isBlank() ? "" : " (" + name + ")")
                    .append(": unless something holds it where it is (safety, a painful moment, a direct question), ")
                    .append("give them the next turn in a natural way, a question or an invitation, without announcing it.\n");
        }
        return out.toString();
    }

    static Map<String, Count> counts(List<Message> sessionMessages, Map<Long, String> roleByMessageId,
                                     List<DirectorDecisionRecord> turns, List<ClientIntent> intents) {
        Map<String, int[]> raw = new LinkedHashMap<>();
        raw.put("OWNER", new int[4]);
        raw.put("MEMBER", new int[4]);
        for (Message message : sessionMessages == null ? List.<Message>of() : sessionMessages) {
            int[] row = raw.get(roleByMessageId.get(message.getId()));
            if (row != null && message.getSenderType() == MessageSenderType.USER && message.getContent() != null) {
                row[0] += message.getContent().trim().isEmpty() ? 0 : message.getContent().trim().split("\\s+").length;
            }
        }
        for (DirectorDecisionRecord turn : turns == null ? List.<DirectorDecisionRecord>of() : turns) {
            String to = turn.getAddressee();
            boolean asking = turn.getAction() != null && turn.getAction().startsWith("ASK_");
            for (String role : List.of("OWNER", "MEMBER")) {
                if (role.equals(to) || "BOTH".equals(to)) {
                    raw.get(role)[1]++;
                    if (asking && role.equals(to)) {
                        raw.get(role)[2]++;
                    }
                }
            }
        }
        for (ClientIntent intent : intents == null ? List.<ClientIntent>of() : intents) {
            int[] row = raw.get(intent.getParticipantRole());
            if (row != null && intent.getStatus() == IntentStatus.CONFIRMED) {
                row[3]++;
            }
        }
        Map<String, Count> out = new LinkedHashMap<>();
        raw.forEach((role, row) -> out.put(role, new Count(row[0], row[1], row[2], row[3])));
        return out;
    }

    /** The partner the attention leans away from, or "" when it is even enough. */
    static String leaning(Map<String, Count> counts) {
        int owner = counts.get("OWNER").addressed();
        int member = counts.get("MEMBER").addressed();
        int total = owner + member;
        if (total < MIN_TURNS) {
            return "";
        }
        if (owner >= total * LEAN) {
            return "MEMBER";
        }
        if (member >= total * LEAN) {
            return "OWNER";
        }
        return "";
    }
}
