package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.SessionRecordRepository;
import com.coupleai.coupleai.beinema.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The household budget book of a conversation with Pouria: lines of income and spending, a monthly
 * limit per category, and what a month and the last three months say. Both people of the household
 * read and write it; Pouria writes in it what they tell him in the talk, marked as his.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    /** How far back the book is read for a month and the averages. */
    static final int READ_DAYS = 500;
    static final long MAX_AMOUNT = 1_000_000_000_000L;

    private final BudgetEntryRepository entries;
    private final BudgetLimitRepository limits;
    private final ChatRoomParticipantRepository participants;
    private final ChatRoomAgentRepository roomAgents;
    private final AgentRepository agents;
    private final SessionRecordRepository sessions;
    private final com.coupleai.coupleai.beinema.Repository.ChatRoomRepository chatRooms;

    /** A line Pouria heard: million tomans, how many days ago (0 today). */
    public record Heard(String category, double millions, int daysAgo, String note) {
    }

    // ------------------------------------------------------------------ who may see it

    /** This person's conversations with Pouria: id, title, and whether a session was held in it. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> rooms(User user) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatRoomParticipant participant : participants.findAllForUser(user.getId())) {
            ChatRoom room = participant.getChatRoom();
            if (!pouria(room.getId())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", room.getId());
            item.put("title", room.getTitle());
            item.put("hadSession", sessions.findTopByChatRoomIdOrderByIdDesc(room.getId()).isPresent());
            out.add(item);
        }
        return out;
    }

    /** Whether the finance tab opens for this person: they held a session with Pouria. */
    @Transactional(readOnly = true)
    public boolean hadPouriaSession(User user) {
        return rooms(user).stream().anyMatch(room -> Boolean.TRUE.equals(room.get("hadSession")));
    }

    boolean pouria(Long roomId) {
        return roomAgents.findActiveAgentIds(roomId).stream()
                .map(agents::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(Agent::getType)
                .anyMatch(type -> type == AgentType.FINANCIAL);
    }

    /** The conversation, when this person is in it and it is with Pouria. */
    public void requireMember(Long roomId, User user) {
        if (roomId == null || !participants.existsByChatRoom_IdAndUser_Id(roomId, user.getId()) || !pouria(roomId)) {
            throw new IllegalArgumentException("این دفتر پیدا نشد.");
        }
    }

    // ------------------------------------------------------------------ writing

    @Transactional
    public Map<String, Object> add(Long roomId, User user, String categoryId, long amount, LocalDate day, String note) {
        requireMember(roomId, user);
        BudgetKit.Category category = BudgetKit.category(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("دسته را انتخاب کن.");
        }
        if (amount <= 0 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("مبلغ را به تومان بنویس.");
        }
        LocalDate today = LocalDate.now(MarketDataService.TEHRAN);
        if (day == null || day.isAfter(today) || day.isBefore(today.minusDays(READ_DAYS))) {
            day = today;
        }
        BudgetEntry entry = entry(roomId, category, amount, day, note, user.getId(), BudgetEntry.PERSON);
        entries.save(entry);
        return Map.of("id", entry.getPublicId());
    }

    public record ReceiptResult(String entryId, String amount, String category, LocalDate date, String note,
                                boolean alreadyRecorded) {
        static ReceiptResult from(BudgetEntry entry, boolean alreadyRecorded) {
            return new ReceiptResult(entry.getPublicId(), Long.toString(entry.getAmount()), entry.getCategory(),
                    entry.getDay(), entry.getNote(), alreadyRecorded);
        }
    }

    /** Called while holding the room's database write lock. */
    ReceiptResult recordReceipt(Long roomId, User user, String fingerprint,
                                BudgetKit.Category category, long amount, LocalDate day, String note) {
        BudgetEntry existing = entries.findByChatRoomIdAndReceiptSha256(roomId, fingerprint).orElse(null);
        if (existing != null) {
            return ReceiptResult.from(existing, true);
        }
        BudgetEntry receipt = entry(roomId, category, amount, day, note, user.getId(), BudgetEntry.POURIA);
        receipt.setReceiptSha256(fingerprint);
        return ReceiptResult.from(entries.save(receipt), false);
    }

    /** What Pouria heard in the talk, written in the book as his; the lines that were not whole are left out. */
    @Transactional
    public int heard(Long roomId, List<Heard> lines) {
        LocalDate today = LocalDate.now(MarketDataService.TEHRAN);
        int written = 0;
        for (Heard line : lines) {
            BudgetKit.Category category = BudgetKit.category(line.category());
            long amount = Math.round(line.millions() * 1_000_000);
            if (category == null || amount <= 0 || amount > MAX_AMOUNT) {
                continue;
            }
            LocalDate day = today.minusDays(Math.max(0, Math.min(60, line.daysAgo())));
            entries.save(entry(roomId, category, amount, day, line.note(), null, BudgetEntry.POURIA));
            written++;
        }
        return written;
    }

    private static BudgetEntry entry(Long roomId, BudgetKit.Category category, long amount, LocalDate day, String note,
                                     Long by, String source) {
        BudgetEntry entry = new BudgetEntry();
        entry.setPublicId(UUID.randomUUID().toString());
        entry.setChatRoomId(roomId);
        entry.setKind(category.kind());
        entry.setCategory(category.id());
        entry.setAmount(amount);
        entry.setDay(day);
        String text = note == null ? "" : note.trim();
        entry.setNote(text.isEmpty() ? null : text.length() > 200 ? text.substring(0, 200) : text);
        entry.setCreatedBy(by);
        entry.setSource(source);
        return entry;
    }

    @Transactional
    public void delete(Long roomId, String entryId, User user) {
        requireMember(roomId, user);
        BudgetEntry entry = entries.findByPublicId(entryId).filter(found -> found.getChatRoomId().equals(roomId))
                .orElseThrow(() -> new IllegalArgumentException("این ردیف پیدا نشد."));
        entries.delete(entry);
    }

    /** The monthly limits (tomans) by category; 0 or less removes a limit. */
    @Transactional
    public void setLimits(Long roomId, User user, Map<String, Long> amounts) {
        if (user != null) {
            requireMember(roomId, user);
        }
        amounts.forEach((categoryId, amount) -> {
            BudgetKit.Category category = BudgetKit.category(categoryId);
            if (category == null || !BudgetKit.EXPENSE.equals(category.kind()) || amount == null) {
                return;
            }
            BudgetLimit limit = limits.findByChatRoomIdAndCategory(roomId, categoryId).orElse(null);
            if (amount <= 0) {
                if (limit != null) {
                    limits.delete(limit);
                }
                return;
            }
            if (limit == null) {
                limit = new BudgetLimit();
                limit.setChatRoomId(roomId);
                limit.setCategory(categoryId);
            }
            limit.setAmount(Math.min(amount, MAX_AMOUNT));
            limits.save(limit);
        });
    }

    // ------------------------------------------------------------------ reading

    /** One month of the book (Jalali, «1405/08»): totals, each category against its limit, and the lines. */
    @Transactional(readOnly = true)
    public Map<String, Object> month(Long roomId, String month, User user, Map<Long, String> names) {
        if (user != null) {
            requireMember(roomId, user);
            if (names == null) {
                names = names(roomId);
            }
        }
        LocalDate today = LocalDate.now(MarketDataService.TEHRAN);
        String current = BudgetKit.month(today);
        String shown = BudgetKit.valid(month) ? month : current;
        List<BudgetEntry> all = entries.findAllByChatRoomIdAndDayGreaterThanEqualOrderByDayDescIdDesc(roomId, today.minusDays(READ_DAYS));
        List<BudgetEntry> mine = all.stream().filter(entry -> BudgetKit.month(entry.getDay()).equals(shown)).toList();
        Totals totals = totals(mine);

        Map<String, Long> limitBy = new LinkedHashMap<>();
        limits.findAllByChatRoomId(roomId).forEach(limit -> limitBy.put(limit.getCategory(), limit.getAmount()));
        List<Map<String, Object>> categories = new ArrayList<>();
        for (BudgetKit.Category category : BudgetKit.CATEGORIES) {
            long spent = mine.stream().filter(entry -> entry.getCategory().equals(category.id())).mapToLong(BudgetEntry::getAmount).sum();
            Long limit = limitBy.get(category.id());
            if (spent == 0 && limit == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", category.id());
            item.put("label", category.label());
            item.put("kind", category.kind());
            item.put("amount", spent);
            item.put("limit", limit);
            item.put("over", limit != null && spent > limit);
            categories.add(item);
        }

        List<Map<String, Object>> lines = new ArrayList<>();
        for (BudgetEntry entry : mine) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", entry.getPublicId());
            line.put("kind", entry.getKind());
            line.put("category", entry.getCategory());
            line.put("label", BudgetKit.category(entry.getCategory()) == null ? entry.getCategory() : BudgetKit.category(entry.getCategory()).label());
            line.put("amount", entry.getAmount());
            line.put("day", MarketDataService.jalali(entry.getDay()));
            line.put("note", entry.getNote());
            line.put("by", BudgetEntry.POURIA.equals(entry.getSource()) ? "پوریا"
                    : names == null ? null : names.getOrDefault(entry.getCreatedBy(), "یکی از شما"));
            line.put("mine", user != null && Objects.equals(entry.getCreatedBy(), user.getId()));
            lines.add(line);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", shown);
        out.put("monthName", BudgetKit.monthName(shown));
        out.put("previous", BudgetKit.shift(shown, -1));
        out.put("next", shown.equals(current) ? null : BudgetKit.shift(shown, 1));
        out.put("current", shown.equals(current));
        out.put("income", totals.income());
        out.put("spending", totals.spending());
        out.put("saved", totals.saved());
        out.put("left", totals.income() - totals.spending() - totals.saved());
        out.put("savingRate", totals.income() <= 0 ? null : (double) (totals.income() - totals.spending()) / totals.income());
        out.put("categories", categories);
        out.put("entries", lines);
        out.put("average", average(all, current));
        out.put("categoryNames", BudgetKit.CATEGORIES);
        return out;
    }

    record Totals(long income, long spending, long saved) {
    }

    static Totals totals(List<BudgetEntry> lines) {
        long income = 0;
        long spending = 0;
        long saved = 0;
        for (BudgetEntry entry : lines) {
            if (BudgetKit.INCOME.equals(entry.getKind())) {
                income += entry.getAmount();
            } else if (BudgetKit.SAVING.equals(entry.getCategory())) {
                saved += entry.getAmount();
            } else {
                spending += entry.getAmount();
            }
        }
        return new Totals(income, spending, saved);
    }

    /** The last three whole months before this one that have lines: income, spending and saving a month on average. */
    static Map<String, Object> average(List<BudgetEntry> all, String current) {
        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            months.add(BudgetKit.shift(current, -i));
        }
        List<String> withLines = months.stream()
                .filter(month -> all.stream().anyMatch(entry -> BudgetKit.month(entry.getDay()).equals(month))).toList();
        if (withLines.isEmpty()) {
            return null;
        }
        Totals sum = totals(all.stream().filter(entry -> withLines.contains(BudgetKit.month(entry.getDay()))).toList());
        int n = withLines.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", n);
        out.put("income", sum.income() / n);
        out.put("spending", sum.spending() / n);
        out.put("saved", sum.saved() / n);
        out.put("left", (sum.income() - sum.spending() - sum.saved()) / n);
        return out;
    }

    // ------------------------------------------------------------------ for Pouria

    /** The BUDGET lines of [FINANCE]: this month against the limits, the averages, and how to write in the book. */
    @Transactional(readOnly = true)
    public String forDirector(Long roomId) {
        return forDirector(roomId, false);
    }

    /** Give recent line details to Pouria only for a fresh request to analyze the budget. */
    @Transactional(readOnly = true)
    public String forDirector(Long roomId, boolean includeEntries) {
        Map<String, Object> now = month(roomId, null, null, null);
        StringBuilder out = new StringBuilder("budget book (tomans, ").append(now.get("monthName")).append(", the household's, both see it): ");
        List<?> lines = (List<?>) now.get("entries");
        if (lines.isEmpty() && now.get("average") == null) {
            out.append("EMPTY\n");
        } else {
            out.append("income ").append(now.get("income")).append(", spending ").append(now.get("spending"))
                    .append(", saved ").append(now.get("saved")).append(", left ").append(now.get("left")).append('\n');
            for (Object item : (List<?>) now.get("categories")) {
                Map<?, ?> category = (Map<?, ?>) item;
                out.append("  ").append(category.get("id")).append(' ').append(category.get("amount"))
                        .append(category.get("limit") == null ? "" : " of limit " + category.get("limit"))
                        .append(Boolean.TRUE.equals(category.get("over")) ? " OVER" : "").append('\n');
            }
            Map<?, ?> average = (Map<?, ?>) now.get("average");
            if (average != null) {
                out.append("  last ").append(average.get("months")).append(" months on average: income ").append(average.get("income"))
                        .append(", spending ").append(average.get("spending")).append(", saved ").append(average.get("saved"))
                        .append(", left ").append(average.get("left")).append('\n');
            }
        }
        out.append(String.format(Locale.ROOT, "  categories: %s%n", String.join(", ",
                BudgetKit.CATEGORIES.stream().map(category -> category.id() + "(" + category.kind().charAt(0) + ")").toList())));
        if (includeEntries) {
            out.append("  recent confirmed budget entries (date, category, amount in tomans):\n");
            lines.stream().limit(20).map(item -> (Map<?, ?>) item).forEach(line ->
                    out.append("  ").append(line.get("day")).append(' ')
                            .append(line.get("category")).append(' ')
                            .append(line.get("amount")).append('\n'));
            out.append("  Analyze these recorded figures only. Ask before changing any budget limit.\n");
        }
        return out.toString();
    }

    /** The names of the people of a conversation, by user id. */
    public Map<Long, String> names(Long roomId) {
        Map<Long, String> out = new LinkedHashMap<>();
        chatRooms.findById(roomId).ifPresent(room -> participants.findAllByChatRoom(room)
                .forEach(person -> out.put(person.getUser().getId(), person.getUser().visibleName())));
        return out;
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        entries.deleteAllByChatRoomId(roomId);
        limits.deleteAllByChatRoomId(roomId);
    }
}
