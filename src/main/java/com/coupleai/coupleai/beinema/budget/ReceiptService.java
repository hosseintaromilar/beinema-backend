package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageAttachmentRepository;
import com.coupleai.coupleai.beinema.files.ChatFileService;
import com.coupleai.coupleai.beinema.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Reads a sent receipt for review and writes its confirmed total to the room's budget. */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private static final String MISSING = "فاکتور در این گفتگو پیدا نشد.";

    private final BudgetService budget;
    private final BudgetEntryRepository entries;
    private final ChatRoomRepository rooms;
    private final ChatRoomParticipantRepository participants;
    private final MessageAttachmentRepository attachments;
    private final ChatFileService files;
    private final ReceiptExtractor extractor;

    public record CategoryOption(String id, String label) {}

    public record Preview(String amount, String category, String date, String note, String currency,
                          String warning, boolean alreadyRecorded, List<CategoryOption> categories) {}

    public record Confirmation(String amount, String category, String date, String note) {}

    private static final List<CategoryOption> EXPENSE_OPTIONS = BudgetKit.CATEGORIES.stream()
            .filter(BudgetKit::isReceiptExpense)
            .map(item -> new CategoryOption(item.id(), item.label()))
            .toList();

    public Preview preview(Long roomId, Long attachmentId, User user) {
        ChatRoom room = rooms.findById(roomId).orElseThrow(() -> new IllegalArgumentException(MISSING));
        MessageAttachment attachment = requireReceipt(room, attachmentId, user);
        BudgetEntry saved = entries.findByChatRoomIdAndReceiptSha256(roomId, attachment.getSha256()).orElse(null);
        if (saved != null) {
            return new Preview(Long.toString(saved.getAmount()), saved.getCategory(), saved.getDay().toString(),
                    saved.getNote(), null, null, true, EXPENSE_OPTIONS);
        }
        ReceiptExtractor.Draft draft = extractor.extract(files.load(roomId, attachmentId));
        String date = draft.date();
        String warning = draft.warning();
        if (date != null && !validDay(parseDay(date))) {
            date = null;
            warning = warning == null ? "تاریخ فاکتور را برای ثبت در دفتر بررسی کن." : warning;
        }
        return new Preview(draft.amount(), draft.category(), date, draft.note(), draft.currency(),
                warning, false, EXPENSE_OPTIONS);
    }

    @Transactional
    public BudgetService.ReceiptResult confirm(Long roomId, Long attachmentId, User user, Confirmation details) {
        ChatRoom room = rooms.lockForReceipt(roomId).orElseThrow(() -> new IllegalArgumentException(MISSING));
        MessageAttachment attachment = requireReceipt(room, attachmentId, user);
        BudgetKit.Category category = BudgetKit.category(details.category());
        if (!BudgetKit.isReceiptExpense(category)) {
            throw new IllegalArgumentException("دستهٔ هزینه را انتخاب کن.");
        }
        long amount = parseAmount(details.amount());
        LocalDate day = parseDay(details.date());
        if (!validDay(day)) {
            throw new IllegalArgumentException("تاریخ باید در ۵۰۰ روز گذشته یا امروز باشد.");
        }
        if (details.note() != null && details.note().length() > 200) {
            throw new IllegalArgumentException("توضیح فاکتور باید حداکثر ۲۰۰ نویسه باشد.");
        }
        return budget.recordReceipt(roomId, user, attachment.getSha256(), category, amount, day, details.note());
    }

    private MessageAttachment requireReceipt(ChatRoom room, Long attachmentId, User user) {
        budget.requireMember(room.getId(), user);
        if (participants.findByChatRoomAndUser(room, user).filter(ChatRoomParticipant::isActiveMember).isEmpty()) {
            throw new IllegalArgumentException(MISSING);
        }
        return attachments.findByIdAndChatRoomId(attachmentId, room.getId())
                .filter(file -> Boolean.TRUE.equals(file.getReceipt()) && user.getId().equals(file.getUploaderId())
                        && file.getMessageId() != null && file.getSha256() != null)
                .orElseThrow(() -> new IllegalArgumentException(MISSING));
    }

    static long parseAmount(String amount) {
        String digits = amount == null ? "" : amount.strip();
        if (digits.matches("[0-9]{1,13}")) {
            long value = Long.parseLong(digits);
            if (value > 0 && value <= BudgetService.MAX_AMOUNT) {
                return value;
            }
        }
        throw new IllegalArgumentException("مبلغ را به‌صورت عدد صحیح و به تومان وارد کن.");
    }

    private static LocalDate parseDay(String date) {
        try {
            return date == null ? null : LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static boolean validDay(LocalDate day) {
        LocalDate today = LocalDate.now(MarketDataService.TEHRAN);
        return day != null && !day.isAfter(today) && !day.isBefore(today.minusDays(BudgetService.READ_DAYS));
    }
}
