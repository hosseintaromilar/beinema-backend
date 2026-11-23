package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomMemberStatus;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageAttachmentRepository;
import com.coupleai.coupleai.beinema.Repository.SessionRecordRepository;
import com.coupleai.coupleai.beinema.files.ChatFileService;
import com.coupleai.coupleai.beinema.market.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReceiptServiceTest {

    private final BudgetEntryRepository entries = mock(BudgetEntryRepository.class);
    private final ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    private final ChatRoomParticipantRepository participants = mock(ChatRoomParticipantRepository.class);
    private final MessageAttachmentRepository attachments = mock(MessageAttachmentRepository.class);
    private final ChatFileService files = mock(ChatFileService.class);
    private final ReceiptExtractor extractor = mock(ReceiptExtractor.class);
    private final BudgetService budget = spy(new BudgetService(entries, mock(BudgetLimitRepository.class),
            participants, mock(ChatRoomAgentRepository.class), mock(AgentRepository.class),
            mock(SessionRecordRepository.class), rooms));
    private final ReceiptService receipts = new ReceiptService(budget, entries, rooms, participants, attachments, files, extractor);
    private final AtomicReference<BudgetEntry> stored = new AtomicReference<>();
    private final ChatRoom room = new ChatRoom();
    private final User user = new User();
    private final ChatRoomParticipant member = new ChatRoomParticipant();
    private final MessageAttachment file = MessageAttachment.builder().receipt(true).chatRoomId(5L)
            .uploaderId(3L).messageId(10L).sha256("same-digest").contentType("image/jpeg").build();
    private final LocalDate now = LocalDate.now(MarketDataService.TEHRAN);

    @BeforeEach
    void setUp() {
        room.setId(5L);
        user.setId(3L);
        doNothing().when(budget).requireMember(5L, user);
        when(rooms.findById(5L)).thenReturn(Optional.of(room));
        when(rooms.lockForReceipt(5L)).thenReturn(Optional.of(room));
        when(participants.findByChatRoomAndUser(room, user)).thenReturn(Optional.of(member));
        when(attachments.findByIdAndChatRoomId(1L, 5L)).thenReturn(Optional.of(file));
        when(entries.findByChatRoomIdAndReceiptSha256(5L, "same-digest"))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(entries.save(any(BudgetEntry.class))).thenAnswer(call -> {
            BudgetEntry entry = call.getArgument(0);
            stored.set(entry);
            return entry;
        });
    }

    @Test
    void previewDoesNotWriteAndConfirmRecordsExactTomans() {
        var loaded = new ChatFileService.LoadedFile(new ByteArrayResource(new byte[]{1}), file);
        when(files.load(5L, 1L)).thenReturn(loaded);
        when(extractor.extract(loaded)).thenReturn(new ReceiptExtractor.Draft(
                "125000", "FOOD", now.toString(), "خرید خانه", "IRR", null));

        ReceiptService.Preview preview = receipts.preview(5L, 1L, user);
        assertEquals("125000", preview.amount());
        assertFalse(preview.alreadyRecorded());
        assertTrue(preview.categories().stream().anyMatch(category -> category.id().equals("FOOD")));
        verify(entries, never()).save(any());

        BudgetService.ReceiptResult result = confirm(1L, "125001", "FOOD", now);
        assertFalse(result.alreadyRecorded());
        assertEquals("125001", result.amount());
        assertEquals(now, result.date());
        assertEquals(125001L, stored.get().getAmount());
        assertEquals(3L, stored.get().getCreatedBy());
        assertEquals(BudgetEntry.POURIA, stored.get().getSource());
    }

    @Test
    void returnsExistingEntryForRetriesAndReuploadsInTheSameRoom() {
        var first = confirm(1L, "50000", "FOOD", now);
        when(attachments.findByIdAndChatRoomId(2L, 5L)).thenReturn(Optional.of(file));
        assertTrue(receipts.preview(5L, 2L, user).alreadyRecorded());
        var repeated = confirm(2L, "63000", "HEALTH", now);
        assertTrue(repeated.alreadyRecorded());
        assertEquals(first.entryId(), repeated.entryId());
        assertEquals("50000", repeated.amount());
        verify(entries, times(1)).save(any());
        verifyNoInteractions(extractor);
    }

    @Test
    void rejectsInvalidAmountsCategoriesAndDatesWithoutWriting() {
        for (String amount : new String[]{"0", "-100", "1.5", "1e3", "1000000000001"}) {
            assertThrows(IllegalArgumentException.class, () -> confirm(1L, amount, "FOOD", now));
        }
        assertThrows(IllegalArgumentException.class, () -> confirm(1L, "500", "SALARY", now));
        assertThrows(IllegalArgumentException.class, () -> confirm(1L, "500", "SAVING", now));
        assertThrows(IllegalArgumentException.class, () -> confirm(1L, "500", "FOOD", now.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> confirm(1L, "500", "FOOD", now.minusDays(501)));
        verify(entries, never()).save(any());
    }

    @Test
    void neverReadsAnUnsentFileOrAnotherPersonsFileOrAFormerMembersFile() {
        file.setMessageId(null);
        assertThrows(IllegalArgumentException.class, () -> receipts.preview(5L, 1L, user));
        file.setMessageId(10L);
        file.setUploaderId(99L);
        assertThrows(IllegalArgumentException.class, () -> confirm(1L, "500", "FOOD", now));
        file.setUploaderId(3L);
        member.setMemberStatus(ChatRoomMemberStatus.LEFT);
        assertThrows(IllegalArgumentException.class, () -> receipts.preview(5L, 1L, user));
        verifyNoInteractions(files, extractor);
        verify(entries, never()).save(any());
    }

    private BudgetService.ReceiptResult confirm(Long attachmentId, String amount, String category, LocalDate date) {
        return receipts.confirm(5L, attachmentId, user,
                new ReceiptService.Confirmation(amount, category, date.toString(), "خرید خانه"));
    }
}
