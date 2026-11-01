package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The lane that runs before the director: should this conversation go on its normal way,
 * or have the people in it said, even between the lines, that going on is pointless?
 * It only opens a question to them; they decide. Any failure means the session goes on.
 */
public final class ClosureGate {

    private static final Logger log = LoggerFactory.getLogger(ClosureGate.class);

    public static final String MODE = "##BEINEMA_MODE=CLOSURE##";
    static final String DEFAULT_QUESTION = "به نظر می‌رسه دیگه ادامه دادن این گفتگو کمکی نمی‌کنه. می‌خواید همین‌جا تمومش کنیم؟";
    private static final int MIN_PERSON_MESSAGES = 2;
    private static final int WINDOW = 10;

    /** question: null when the session goes on. */
    public record Verdict(String question, String reason, long millis) {
    }

    private ClosureGate() {
    }

    static Verdict check(TurnMaterial m, BrainGateway brain) {
        List<Message> all = new ArrayList<>(m.recent());
        all.addAll(m.fresh());
        long fromPeople = all.stream().filter(message -> message.getSenderType() == MessageSenderType.USER).count();
        if (fromPeople < MIN_PERSON_MESSAGES || (m.screen() != null && m.screen().any())) {
            return null;
        }
        try {
            BrainGateway.Reply reply = brain.forRoom(m.room()).closure(packet(m, all));
            return read(reply.text(), reply.millis());
        } catch (RuntimeException exception) {
            log.warn("Closure check failed in room {}: {}", m.roomId(), exception.getMessage());
            return null;
        }
    }

    static String packet(TurnMaterial m, List<Message> all) {
        StringBuilder out = new StringBuilder("[PARTICIPANTS]\n");
        for (TurnMaterial.Partner partner : m.partners()) {
            out.append(partner.role()).append('=').append(partner.name()).append('\n');
        }
        out.append("[RECENT_MESSAGES]\n");
        for (Message message : all.subList(Math.max(0, all.size() - WINDOW), all.size())) {
            out.append(DirectorPacketBuilder.line(m, message, 500)).append('\n');
        }
        return MODE + "\n" + DirectorPacketBuilder.neutralize(out.toString());
    }

    /** Only a clear ENDING with a usable reply stops the normal way. */
    static Verdict read(String raw, long millis) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode root = CounselingStateStore.JSON.readTree(raw.substring(start, end + 1));
            if (!"ENDING".equals(root.path("verdict").asText(""))) {
                return null;
            }
            String question = root.path("question").asText("").trim();
            return new Verdict(question.isEmpty() ? DEFAULT_QUESTION : PersianText.clip(question, 240),
                    PersianText.clip(root.path("why").asText(""), 200), millis);
        } catch (Exception exception) {
            return null;
        }
    }
}
