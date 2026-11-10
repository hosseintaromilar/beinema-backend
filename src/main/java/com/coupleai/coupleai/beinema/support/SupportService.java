package com.coupleai.coupleai.beinema.support;

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
    private final com.coupleai.coupleai.beinema.mail.SmtpMailer mailer;

    /** Where support is told of a new ticket or a person's reply (SUPPORT_NOTIFY_EMAIL); empty: nobody. */
    @org.springframework.beans.factory.annotation.Value("${beinema.support.notify-email:}")
    private String notifyEmail;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SupportService.class);

    public record TicketSummary(Long id, String subject, String category, String status, boolean unread,
                                Long lastActivityAt, Long createdAt, String userName, Long userId) {
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
        tellSupport(ticket, user.visibleName(), text, true);
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
        tellSupport(ticket, nameOf(ticket.getUserId()), text, false);
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
        if (ticket.getEmailFrom() != null) {
            // a ticket that came by email is answered by email too, in the same thread
            answerByEmail(ticket, text);
        }
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

    /**
     * An email that reached support@ or info@: joins the ticket it answers (the [#id] in the subject,
     * from the same address), or the address's open email ticket, or starts a new one. userId is the
     * account of the address, or 0. Returns the ticket's id.
     */
    @Transactional
    public Long fromEmail(Long userId, String from, String mailbox, String subject, String body, String messageId) {
        String text = body.length() > MAX_BODY ? body.substring(0, MAX_BODY - 1) + "…" : body;
        SupportTicket ticket = null;
        java.util.regex.Matcher tag = java.util.regex.Pattern.compile("\\[#(\\d{1,10})]").matcher(subject == null ? "" : subject);
        if (tag.find()) {
            ticket = ticketRepository.findById(Long.valueOf(tag.group(1)))
                    .filter(found -> from.equalsIgnoreCase(found.getEmailFrom()))
                    .orElse(null);
        }
        if (ticket == null) {
            ticket = ticketRepository.findAllByEmailFromIgnoreCaseAndStatusNotOrderByLastActivityAtDesc(from, SupportTicketStatus.CLOSED)
                    .stream().findFirst().orElse(null);
        }
        boolean fresh = ticket == null;
        if (ticket == null) {
            ticket = new SupportTicket();
            ticket.setUserId(userId == null ? 0L : userId);
            String title = subject == null || subject.isBlank() ? "ایمیل از " + from
                    : subject.replaceAll("^(?i)((re|fwd?|پاسخ)\\s*:\\s*)+", "").strip();
            ticket.setSubject(title.length() > MAX_SUBJECT ? title.substring(0, MAX_SUBJECT) : title);
            ticket.setCategory("EMAIL");
            ticket.setEmailFrom(from);
        }
        ticket.setEmailMailbox(mailbox);
        if (messageId != null && messageId.length() <= 300) {
            ticket.setEmailMessageId(messageId);
        }
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setLastActivityAt(Instant.now());
        ticket = ticketRepository.save(ticket);
        addMessage(ticket.getId(), false, ticket.getUserId() == 0L ? null : ticket.getUserId(), text);
        tellSupport(ticket, from + " (ایمیل)", text, fresh);
        return ticket.getId();
    }

    /**
     * An email to support about a new ticket or a person's reply, sent in the background so a
     * slow or failing mail server never holds up or breaks the ticket. Only the start of the text.
     */
    private void tellSupport(SupportTicket ticket, String who, String text, boolean fresh) {
        if (notifyEmail == null || notifyEmail.isBlank() || !mailer.configured()) {
            return;
        }
        String subject = (fresh ? "تیکت تازه" : "پاسخ تازه در تیکت") + " #" + ticket.getId() + ": " + ticket.getSubject();
        String body = (fresh ? "یک تیکت تازه ثبت شد." : "کاربر به تیکت جواب داد.") + "\n\n"
                + "از: " + who + "\n"
                + "موضوع: " + ticket.getSubject() + "\n\n"
                + (text.length() > 600 ? text.substring(0, 600) + "…" : text) + "\n\n"
                + "برای دیدن و جواب دادن: https://admin.beinema.ir/tickets";
        String to = notifyEmail.trim();
        Thread thread = new Thread(() -> {
            try {
                mailer.send(to, subject, body);
            } catch (RuntimeException failure) {
                log.warn("Support was not told of ticket {}: {}", ticket.getId(), failure.getMessage());
            }
        }, "beinema-ticket-" + ticket.getId());
        thread.setDaemon(true);
        thread.start();
    }

    private void answerByEmail(SupportTicket ticket, String text) {
        String box = "info".equals(ticket.getEmailMailbox()) ? "info" : "support";
        String subject = ticket.getSubject().replaceAll("\\s*\\[#\\d+]$", "");
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        if (ticket.getEmailMessageId() != null) {
            headers.put("In-Reply-To", ticket.getEmailMessageId());
            headers.put("References", ticket.getEmailMessageId());
        }
        try {
            mailer.send(new com.coupleai.coupleai.beinema.mail.SmtpMailer.Mail(ticket.getEmailFrom(), box + "@beinema.ir",
                    "info".equals(box) ? "بین‌ما" : "پشتیبانی بین‌ما",
                    "Re: " + subject + " [#" + ticket.getId() + "]", text, null, null, headers));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("ایمیل پاسخ فرستاده نشد؛ پاسخ ثبت نشد. کمی بعد دوباره بفرست.");
        }
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
        // a ticket from email shows its address next to the name (or alone, for an unknown address)
        String who = ticket.getEmailFrom() == null || userName == null ? userName
                : ticket.getUserId() == 0L ? ticket.getEmailFrom() : userName + " · " + ticket.getEmailFrom();
        return new TicketSummary(ticket.getId(), ticket.getSubject(), ticket.getCategory(), ticket.getStatus().name(),
                ticket.isUnreadByUser(),
                ticket.getLastActivityAt() == null ? null : ticket.getLastActivityAt().toEpochMilli(),
                millis(ticket.getCreatedAt()), who,
                // only the admin's view (with a name) links to the account; 0 is an email from an unknown address
                userName == null || ticket.getUserId() == null || ticket.getUserId() == 0L ? null : ticket.getUserId());
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
