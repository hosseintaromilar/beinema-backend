package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.SupportTicket;
import com.coupleai.coupleai.beinema.Entity.SupportTicketMessage;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.SupportTicketStatus;
import com.coupleai.coupleai.beinema.Repository.SupportTicketMessageRepository;
import com.coupleai.coupleai.beinema.Repository.SupportTicketRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Support tickets: a person writes to support from the dashboard, support answers
 * in the admin panel, and the conversation goes on in the same ticket until it
 * is closed (a new message from the person opens it again).
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    static final Set<String> CATEGORIES = Set.of("ACCOUNT", "PAYMENT", "CHAT", "BUG", "OTHER");
    static final int MAX_SUBJECT = 150;
    static final int MAX_BODY = 4000;
    static final int MAX_OPEN_PER_PERSON = 5;

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public record TicketSummary(Long id, String subject, String category, String status, boolean unread,
                                Long lastActivityAt, Long createdAt, String userName) {
    }

    public record TicketMessage(Long id, boolean fromSupport, String body, Long createdAt) {
    }

    public record TicketView(TicketSummary ticket, List<TicketMessage> messages) {
    }

    // ------------------------------------------------------------ the person

    @Transactional
    public TicketView create(String subject, String category, String body) {
        User user = currentUserService.getCurrentUser();
        String title = clean(subject, MAX_SUBJECT, "موضوع را بنویس.");
        String text = clean(body, MAX_BODY, "متن پیامت را بنویس.");
        String kind = category != null && CATEGORIES.contains(category) ? category : "OTHER";
        if (ticketRepository.countByUserIdAndStatusNot(user.getId(), SupportTicketStatus.CLOSED) >= MAX_OPEN_PER_PERSON) {
            throw new IllegalArgumentException("چند تیکت باز داری؛ لطفاً در همان‌ها ادامه بده یا یکی را ببند.");
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(user.getId());
        ticket.setSubject(title);
        ticket.setCategory(kind);
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setLastActivityAt(Instant.now());
        ticket = ticketRepository.save(ticket);
        addMessage(ticket.getId(), false, user.getId(), text);
        return view(ticket);
    }

    public List<TicketSummary> mine() {
        User user = currentUserService.getCurrentUser();
        return ticketRepository.findAllByUserIdOrderByLastActivityAtDesc(user.getId()).stream()
                .map(ticket -> summary(ticket, null))
                .toList();
    }

    /** Opening a ticket marks support's answer as read. */
    @Transactional
    public TicketView openMine(Long ticketId) {
        SupportTicket ticket = ownTicket(ticketId);
        if (ticket.isUnreadByUser()) {
            ticket.setUnreadByUser(false);
            ticketRepository.save(ticket);
        }
        return view(ticket);
    }

    @Transactional
    public TicketView replyMine(Long ticketId, String body) {
        SupportTicket ticket = ownTicket(ticketId);
        String text = clean(body, MAX_BODY, "متن پیامت را بنویس.");
        addMessage(ticket.getId(), false, ticket.getUserId(), text);
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setLastActivityAt(Instant.now());
        ticketRepository.save(ticket);
        return view(ticket);
    }

    @Transactional
    public TicketView closeMine(Long ticketId) {
        SupportTicket ticket = ownTicket(ticketId);
        ticket.setStatus(SupportTicketStatus.CLOSED);
        ticketRepository.save(ticket);
        return view(ticket);
    }

    public long unreadForMe() {
        return ticketRepository.countByUserIdAndUnreadByUserTrue(currentUserService.getCurrentUser().getId());
    }

    // ------------------------------------------------------------ support

    public List<TicketSummary> forAdmin(String status) {
        List<SupportTicket> tickets = status == null || status.isBlank()
                ? ticketRepository.findTop200ByOrderByLastActivityAtDesc()
                : ticketRepository.findAllByStatusOrderByLastActivityAtDesc(SupportTicketStatus.valueOf(status));
        Map<Long, String> names = new HashMap<>();
        return tickets.stream()
                .map(ticket -> summary(ticket, names.computeIfAbsent(ticket.getUserId(), this::nameOf)))
                .toList();
    }

    public TicketView adminView(Long ticketId) {
        SupportTicket ticket = anyTicket(ticketId);
        return new TicketView(summary(ticket, nameOf(ticket.getUserId())), messages(ticket.getId()));
    }

    @Transactional
    public TicketView adminReply(Long ticketId, String body) {
        SupportTicket ticket = anyTicket(ticketId);
        String text = clean(body, MAX_BODY, "متن پاسخ را بنویس.");
        addMessage(ticket.getId(), true, currentUserService.getCurrentUser().getId(), text);
        ticket.setStatus(SupportTicketStatus.ANSWERED);
        ticket.setUnreadByUser(true);
        ticket.setLastActivityAt(Instant.now());
        ticketRepository.save(ticket);
        return adminView(ticketId);
    }

    @Transactional
    public TicketView adminClose(Long ticketId) {
        SupportTicket ticket = anyTicket(ticketId);
        ticket.setStatus(SupportTicketStatus.CLOSED);
        ticketRepository.save(ticket);
        return adminView(ticketId);
    }

    public long openCount() {
        return ticketRepository.countByStatus(SupportTicketStatus.OPEN);
    }

    // ------------------------------------------------------------ helpers

    private void addMessage(Long ticketId, boolean fromSupport, Long authorId, String body) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicketId(ticketId);
        message.setFromSupport(fromSupport);
        message.setAuthorId(authorId);
        message.setBody(body);
        messageRepository.save(message);
    }

    private SupportTicket ownTicket(Long ticketId) {
        User user = currentUserService.getCurrentUser();
        return ticketRepository.findById(ticketId)
                .filter(ticket -> ticket.getUserId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("تیکت پیدا نشد."));
    }

    private SupportTicket anyTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("تیکت پیدا نشد."));
    }

    private TicketView view(SupportTicket ticket) {
        return new TicketView(summary(ticket, null), messages(ticket.getId()));
    }

    private List<TicketMessage> messages(Long ticketId) {
        return messageRepository.findAllByTicketIdOrderByIdAsc(ticketId).stream()
                .map(message -> new TicketMessage(message.getId(), message.isFromSupport(), message.getBody(),
                        millis(message.getCreatedAt())))
                .toList();
    }

    private TicketSummary summary(SupportTicket ticket, String userName) {
        return new TicketSummary(ticket.getId(), ticket.getSubject(), ticket.getCategory(), ticket.getStatus().name(),
                ticket.isUnreadByUser(),
                ticket.getLastActivityAt() == null ? null : ticket.getLastActivityAt().toEpochMilli(),
                millis(ticket.getCreatedAt()), userName);
    }

    private String nameOf(Long userId) {
        return userRepository.findById(userId).map(User::visibleName).orElse("-");
    }

    private static String clean(String value, int max, String emptyMessage) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (text.length() > max) {
            throw new IllegalArgumentException("متن خیلی طولانی است.");
        }
        return text;
    }

    private static Long millis(java.time.LocalDateTime time) {
        return time == null ? null : time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
