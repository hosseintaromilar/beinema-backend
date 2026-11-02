package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidentialMessageTest {

    private static Message message(Long visibleTo) {
        return Message.builder().senderType(MessageSenderType.USER).content("سلام").visibleToUserId(visibleTo).build();
    }

    @Test
    void aSealedMessageIsNeverPartOfTheConversation() {
        assertTrue(MessageTexts.countsAsConversation(message(null)));
        assertFalse(MessageTexts.countsAsConversation(message(7L)));
    }

    @Test
    void theReplyAndItsKindAreRead() {
        ConfidentialMessageService.Reply safety = ConfidentialMessageService.read("{\"kind\":\"SAFETY\",\"reply\":\"الان در امانی؟\"}");
        assertTrue(safety.safety());
        assertEquals("الان در امانی؟", safety.text());
        assertFalse(ConfidentialMessageService.read("{\"kind\":\"OTHER\",\"reply\":\"باشه\"}").safety());
    }

    @Test
    void aFailedLaneGivesNoReply() {
        assertNull(ConfidentialMessageService.read("{\"schema\":\"private.v1\",\"error\":\"PRIVATE_FAILED\"}"));
        assertNull(ConfidentialMessageService.read(""));
    }
}
