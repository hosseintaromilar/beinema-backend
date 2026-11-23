package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomAgent;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.files.ChatFileService;
import com.coupleai.coupleai.beinema.market.MarketDataService;
import com.coupleai.coupleai.beinema.metis.MetisService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = "beinema.market.enabled=false")
@Transactional
class ReceiptIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private ChatFileService files;
    @Autowired private ReceiptService receipts;
    @Autowired private BudgetService budget;
    @MockitoBean private MetisService metis;

    @Test
    void confirmedReceiptAppearsOnceInTheBudgetEvenWhenUploadedTwice() {
        User user = User.builder().name("Receipt Integration Test")
                .email("receipt-" + UUID.randomUUID() + "@example.invalid").password("unused").build();
        entityManager.persist(user);
        ChatRoom room = ChatRoom.builder().title("Receipt Integration Test").createdBy(user.getId()).build();
        entityManager.persist(room);
        entityManager.persist(ChatRoomParticipant.builder().chatRoom(room).user(user).role(ParticipantRole.OWNER).build());
        Agent agent = Agent.builder().name("Receipt Integration Test").type(AgentType.FINANCIAL).aiBotId("unused").build();
        entityManager.persist(agent);
        entityManager.persist(ChatRoomAgent.builder().chatRoom(room).agent(agent).addedBy(user.getId()).build());

        String fingerprint = "a".repeat(64);
        Long firstAttachment = sentReceipt(room, user, fingerprint, 1);
        Long secondAttachment = sentReceipt(room, user, fingerprint, 2);
        assertNotEquals(firstAttachment, secondAttachment);
        entityManager.clear();

        LocalDate today = LocalDate.now(MarketDataService.TEHRAN);
        BudgetService.ReceiptResult first = receipts.confirm(room.getId(), firstAttachment, user,
                new ReceiptService.Confirmation("123457", "FOOD", today.toString(), "خرید خوراک"));
        entityManager.flush();
        entityManager.clear();
        BudgetService.ReceiptResult duplicate = receipts.confirm(room.getId(), secondAttachment, user,
                new ReceiptService.Confirmation("90000", "BILLS", today.toString(), "توضیح دیگر"));

        assertFalse(first.alreadyRecorded());
        assertTrue(duplicate.alreadyRecorded());
        assertEquals(first.entryId(), duplicate.entryId());
        assertEquals("123457", duplicate.amount());
        assertEquals("FOOD", duplicate.category());
        assertEquals(today, duplicate.date());
        assertEquals("خرید خوراک", duplicate.note());

        Map<String, Object> month = budget.month(room.getId(), BudgetKit.month(today), user, null);
        assertEquals(123_457L, month.get("spending"));
        List<?> lines = (List<?>) month.get("entries");
        assertEquals(1, lines.size());
        Map<?, ?> line = (Map<?, ?>) lines.get(0);
        assertEquals(first.entryId(), line.get("id"));
        assertEquals(123_457L, line.get("amount"));
        assertEquals("FOOD", line.get("category"));
        verifyNoInteractions(metis);
    }

    private Long sentReceipt(ChatRoom room, User user, String fingerprint, int sequence) {
        MessageAttachment attachment = MessageAttachment.builder()
                .chatRoomId(room.getId()).uploaderId(user.getId()).originalName("receipt.png")
                .contentType("image/png").sizeBytes(1L).kind("IMAGE").receipt(true)
                .storagePath("integration-test-unused-" + sequence).sha256(fingerprint)
                .summaryStatus(MessageAttachment.READY).build();
        entityManager.persist(attachment);
        Message message = Message.builder().chatRoom(room).senderType(MessageSenderType.USER)
                .senderId(user.getId()).sequenceNumber(sequence).content("فاکتور").build();
        files.saveMessageWithAttachments(message, files.prepare(room.getId(), user.getId(), List.of(attachment.getId())));
        return attachment.getId();
    }
}
