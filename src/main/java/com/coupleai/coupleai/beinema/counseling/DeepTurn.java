package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.DecisionStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * When a turn is worth the director thinking first. Thinking makes a turn several times slower, so most
 * turns go without it; these are the ones where a quick decision is most likely to miss: a safety cue,
 * both partners writing in the same turn, guesses they answered in their own words instead, a long
 * message, or a turn before this one that failed.
 */
public final class DeepTurn {

    /** The director lane with thinking on (its own node in each companion's workflow). */
    public static final String MODE = "##BEINEMA_MODE=DIRECTOR_DEEP##";
    static final int LONG_MESSAGE = 600;

    private DeepTurn() {
    }

    /** Why this turn should think first, or null for a quick turn. */
    static String reason(TurnMaterial m) {
        if (m.screen() != null && m.screen().any()) {
            return "a safety cue";
        }
        Set<String> writers = m.fresh().stream()
                .filter(message -> message.getSenderType() == MessageSenderType.USER)
                .map(m::roleOf)
                .collect(Collectors.toSet());
        if (writers.contains("OWNER") && writers.contains("MEMBER")) {
            return "both partners wrote in this turn";
        }
        for (ClarifyingQuestion answered : m.answered() == null ? java.util.List.<ClarifyingQuestion>of() : m.answered()) {
            if (answered.getFreeText() != null && !answered.getFreeText().isBlank()) {
                return "a card was answered in their own words";
            }
        }
        for (Message message : m.fresh()) {
            if (message.getSenderType() == MessageSenderType.USER && message.getContent() != null
                    && message.getContent().strip().length() > LONG_MESSAGE) {
                return "a long message";
            }
        }
        if (m.lastTurns() != null && !m.lastTurns().isEmpty() && m.lastTurns().get(0).getStatus() == DecisionStatus.FALLBACK) {
            return "the turn before failed";
        }
        return null;
    }

    /** The same packet, sent to the thinking lane. */
    static String deep(String packet) {
        return packet.startsWith(DirectorPacketBuilder.MODE)
                ? MODE + packet.substring(DirectorPacketBuilder.MODE.length())
                : packet;
    }
}
