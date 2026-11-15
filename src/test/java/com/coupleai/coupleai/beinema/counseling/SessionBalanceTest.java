package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionBalanceTest {

    private static DirectorDecisionRecord turn(String action, String addressee) {
        DirectorDecisionRecord record = new DirectorDecisionRecord();
        record.setAction(action);
        record.setAddressee(addressee);
        return record;
    }

    private static Message message(long id, String text) {
        Message message = new Message();
        message.setId(id);
        message.setSenderType(MessageSenderType.USER);
        message.setContent(text);
        return message;
    }

    private static final Set<String> COUPLE = Set.of("OWNER", "MEMBER");
    private static final Map<String, String> NAMES = Map.of("OWNER", "سارا", "MEMBER", "علی");

    @Test
    void whenTheAttentionLeansTheQuietOneIsNamedGently() {
        List<DirectorDecisionRecord> turns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            turns.add(turn("ASK_OPEN", "OWNER"));
        }
        turns.add(turn("RESPOND", "MEMBER"));
        String line = SessionBalance.line(COUPLE, NAMES,
                List.of(message(1, "خیلی خسته‌ام از این وضع"), message(2, "منم")),
                Map.of(1L, "OWNER", 2L, "MEMBER"), turns, List.of());

        assertTrue(line.contains("OWNER wrote 5 words, was spoken to in 5 turns, asked 5"));
        assertTrue(line.contains("leans away from MEMBER (علی)"));
        assertTrue(line.contains("without announcing it"), "a suggestion, never an order");
    }

    @Test
    void anEvenSessionOnlyCounts() {
        List<DirectorDecisionRecord> turns = List.of(turn("ASK_OPEN", "OWNER"), turn("ASK_OPEN", "MEMBER"),
                turn("REFLECT", "BOTH"), turn("RESPOND", "OWNER"), turn("ASK_OPEN", "MEMBER"));
        String line = SessionBalance.line(COUPLE, NAMES, List.of(), Map.of(), turns, List.of());
        assertTrue(line.startsWith("balance this session:"));
        assertFalse(line.contains("leans"));
    }

    @Test
    void nothingForOnePersonOrAFewTurns() {
        List<DirectorDecisionRecord> many = List.of(turn("ASK_OPEN", "OWNER"), turn("ASK_OPEN", "OWNER"),
                turn("ASK_OPEN", "OWNER"), turn("ASK_OPEN", "OWNER"), turn("ASK_OPEN", "OWNER"));
        assertEquals("", SessionBalance.line(Set.of("OWNER"), NAMES, List.of(), Map.of(), many, List.of()),
                "alone in the room there is no balance to keep");
        assertEquals("", SessionBalance.line(COUPLE, NAMES, List.of(), Map.of(), many.subList(0, 2), List.of()),
                "too early to say");
    }
}
