package com.coupleai.coupleai.beinema.inbox;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The users' inbox (docs: 02-user-inbox-spec-FA.md). System notes go to one person; the admin
 * sends to one person, a segment or everyone, now or at a time. Segments come only from account
 * and purchase data, never from conversations. Recipients are fixed when a message is sent, and
 * every send, cancel and revoke is in the audit log.
 */
@Service
@RequiredArgsConstructor
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);
    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    private static final Set<String> FORBIDDEN = Set.of("لانچ", "افتتاحیه");
    private static final int BROADCASTS_PER_DAY = 3;
    private static final int PERSONAL_PER_HOUR = 30;
    private static final int BATCH = 500;

    private final InboxMessageRepository messageRepository;
    private final InboxRecipientRepository recipientRepository;
    private final InboxAuditRepository auditRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    /** What the admin fills in. audience: user, segment or all; segment: plan, free_only, expiring, registered_after. */
    public record Draft(String type, String title, String body, String ctaLabel, String ctaPath, String priority,
                        String audience, String segment, String value, Long scheduledAt, String idempotencyKey) {
    }

    public record Item(Long id, String type, String title, String body, String ctaLabel, String ctaPath,
                       String priority, Long sentAt, boolean read, boolean archived) {
    }

    // ------------------------------------------------------------------ system notes

    /** A note from the system to one person (payment, gift, renewal). Never throws. */
    public void system(Long userId, String title, String body, String ctaLabel, String ctaPath) {
        if (userId == null) {
            return;
        }
        try {
            InboxMessage message = new InboxMessage();
            message.setType(InboxMessage.SYSTEM);
            message.setTitle(cut(title, 80));
            message.setBody(cut(body, 2000));
            message.setCtaLabel(ctaLabel);
            message.setCtaPath(ctaPath);
            message.setAudienceKind("user");
            message.setAudienceFilter("user:" + userId);
            message.setStatus(InboxMessage.SENT);
            message.setSentAt(Instant.now());
            message.setRecipientCount(1);
            message = messageRepository.save(message);
            InboxRecipient recipient = new InboxRecipient();
            recipient.setMessageId(message.getId());
            recipient.setUserId(userId);
            recipientRepository.save(recipient);
        } catch (RuntimeException failure) {
            log.warn("Could not put a system note in the inbox of user {}: {}", userId, failure.getMessage());
        }
    }

    // ------------------------------------------------------------------ the person's side

    @Transactional(readOnly = true)
    public List<Item> mine(Long userId, String status) {
        List<Item> items = new ArrayList<>();
        for (InboxRecipient row : recipientRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdDesc(userId)) {
            InboxMessage message = messageRepository.findById(row.getMessageId()).orElse(null);
            if (message == null || InboxMessage.REVOKED.equals(message.getStatus()) || message.getSentAt() == null) {
                continue;
            }
            boolean archived = row.getArchivedAt() != null;
            boolean read = row.getReadAt() != null;
            if ("archived".equals(status) != archived || ("unread".equals(status) && read)) {
                continue;
            }
            items.add(new Item(message.getId(), message.getType(), message.getTitle(), message.getBody(),
                    message.getCtaLabel(), message.getCtaPath(), message.getPriority(),
                    message.getSentAt().toEpochMilli(), read, archived));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return mine(userId, "unread").size();
    }

    @Transactional
    public void read(Long userId, Long messageId) {
        recipientRepository.findByMessageIdAndUserId(messageId, userId).ifPresent(row -> {
            if (row.getReadAt() == null) {
                row.setReadAt(Instant.now());
                recipientRepository.save(row);
            }
        });
    }

    @Transactional
    public void readAll(Long userId) {
        Instant now = Instant.now();
        List<InboxRecipient> rows = recipientRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdDesc(userId).stream()
                .filter(row -> row.getReadAt() == null)
                .toList();
        rows.forEach(row -> row.setReadAt(now));
        recipientRepository.saveAll(rows);
    }

    @Transactional
    public void archive(Long userId, Long messageId) {
        recipientRepository.findByMessageIdAndUserId(messageId, userId).ifPresent(row -> {
            row.setArchivedAt(Instant.now());
            if (row.getReadAt() == null) {
                row.setReadAt(Instant.now());
            }
            recipientRepository.save(row);
        });
    }

    @Transactional
    public void delete(Long userId, Long messageId) {
        recipientRepository.findByMessageIdAndUserId(messageId, userId).ifPresent(row -> {
            row.setDeletedAt(Instant.now());
            recipientRepository.save(row);
        });
    }

    // ------------------------------------------------------------------ the admin's side

    /** How many people a draft would reach now. */
    @Transactional(readOnly = true)
    public int audienceSize(Draft draft) {
        return audience(draft).size();
    }

    /** Sends now, or keeps it for its time (Tehran time is only how it is shown). */
    @Transactional
    public InboxMessage create(User admin, Draft draft, String ip) {
        if (draft.idempotencyKey() != null && !draft.idempotencyKey().isBlank()) {
            var earlier = messageRepository.findByIdempotencyKey(draft.idempotencyKey());
            if (earlier.isPresent()) {
                return earlier.get();
            }
        }
        String type = InboxMessage.PERSONAL.equals(draft.type()) ? InboxMessage.PERSONAL : InboxMessage.ANNOUNCEMENT;
        String title = String.valueOf(draft.title() == null ? "" : draft.title()).trim();
        String body = String.valueOf(draft.body() == null ? "" : draft.body()).trim();
        if (title.isEmpty() || title.length() > 80 || body.isEmpty() || body.length() > 2000) {
            throw new IllegalArgumentException("عنوان (تا ۸۰ نویسه) و متن (تا ۲۰۰۰ نویسه) لازم است.");
        }
        for (String word : FORBIDDEN) {
            if (title.contains(word) || body.contains(word)) {
                throw new IllegalArgumentException("کلمهٔ «" + word + "» در پیام‌ها استفاده نمی‌شود.");
            }
        }
        String path = draft.ctaPath() == null ? null : draft.ctaPath().trim();
        if (path != null && !path.isEmpty() && (!path.startsWith("/") || path.startsWith("//"))) {
            throw new IllegalArgumentException("مسیر دکمه باید داخل سایت باشد، مثل /dashboard/wallet.");
        }
        String audienceKind = "user".equals(draft.audience()) ? "user" : "segment".equals(draft.audience()) ? "segment" : "all";
        LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
        if (!"user".equals(audienceKind)
                && messageRepository.countBySenderIdIsNotNullAndAudienceKindNotAndCreatedAtAfter("user", dayAgo) >= BROADCASTS_PER_DAY) {
            throw new IllegalArgumentException("سقف ارسال گروهی و همگانی امروز (۳ بار) پر شده است.");
        }
        if ("user".equals(audienceKind)
                && messageRepository.countBySenderIdAndAudienceKindAndCreatedAtAfter(admin.getId(), "user",
                LocalDateTime.now().minusHours(1)) >= PERSONAL_PER_HOUR) {
            throw new IllegalArgumentException("سقف پیام شخصی در این ساعت (۳۰) پر شده است.");
        }
        Instant when = draft.scheduledAt() == null ? null : Instant.ofEpochMilli(draft.scheduledAt());
        if (when != null && when.isBefore(Instant.now().minus(Duration.ofMinutes(1)))) {
            throw new IllegalArgumentException("زمان ارسال گذشته است.");
        }
        InboxMessage message = new InboxMessage();
        message.setType(type);
        message.setTitle(title);
        message.setBody(body);
        message.setCtaLabel(blankToNull(draft.ctaLabel()));
        message.setCtaPath(blankToNull(path));
        message.setPriority("important".equals(draft.priority()) ? "important" : "normal");
        message.setSenderId(admin.getId());
        message.setAudienceKind(audienceKind);
        message.setAudienceFilter(cut(audienceKind + ":" + (draft.segment() == null ? "" : draft.segment())
                + ":" + (draft.value() == null ? "" : draft.value()), 200));
        message.setIdempotencyKey(blankToNull(draft.idempotencyKey()));
        message.setScheduledAt(when);
        message.setStatus(InboxMessage.SCHEDULED);
        message = messageRepository.save(message);
        audit(admin.getId(), when == null ? "SEND" : "SCHEDULE", message.getId(), message.getAudienceFilter(), ip);
        if (when == null || !when.isAfter(Instant.now())) {
            deliver(message, draft);
        }
        return message;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    @Transactional
    public void sendDue() {
        for (InboxMessage message : messageRepository.findAllByStatusAndScheduledAtBefore(InboxMessage.SCHEDULED, Instant.now())) {
            String[] parts = String.valueOf(message.getAudienceFilter()).split(":", 3);
            deliver(message, new Draft(message.getType(), null, null, null, null, null, parts[0],
                    parts.length > 1 ? parts[1] : null, parts.length > 2 ? parts[2] : null, null, null));
        }
    }

    private void deliver(InboxMessage message, Draft draft) {
        List<Long> people = audience(draft);
        List<InboxRecipient> batch = new ArrayList<>();
        for (Long userId : people) {
            InboxRecipient row = new InboxRecipient();
            row.setMessageId(message.getId());
            row.setUserId(userId);
            batch.add(row);
            if (batch.size() == BATCH) {
                recipientRepository.saveAll(batch);
                batch.clear();
            }
        }
        recipientRepository.saveAll(batch);
        message.setRecipientCount(people.size());
        message.setStatus(InboxMessage.SENT);
        message.setSentAt(Instant.now());
        messageRepository.save(message);
    }

    @Transactional
    public void cancel(User admin, Long id, String ip) {
        InboxMessage message = messageRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("پیام پیدا نشد."));
        if (!InboxMessage.SCHEDULED.equals(message.getStatus())) {
            throw new IllegalArgumentException("فقط پیام زمان‌بندی‌شده‌ای که هنوز نرفته لغو می‌شود.");
        }
        message.setStatus(InboxMessage.CANCELLED);
        messageRepository.save(message);
        audit(admin.getId(), "CANCEL", id, null, ip);
    }

    /** Takes a sent message out of every inbox; it stays in the history. */
    @Transactional
    public void revoke(User admin, Long id, String ip) {
        InboxMessage message = messageRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("پیام پیدا نشد."));
        message.setStatus(InboxMessage.REVOKED);
        message.setRevokedAt(Instant.now());
        messageRepository.save(message);
        audit(admin.getId(), "REVOKE", id, null, ip);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history() {
        return messageRepository.findTop100ByTypeNotOrderByIdDesc(InboxMessage.SYSTEM).stream().map(message -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", message.getId());
            row.put("type", message.getType());
            row.put("title", message.getTitle());
            row.put("audience", message.getAudienceFilter());
            row.put("status", message.getStatus());
            row.put("recipients", message.getRecipientCount());
            row.put("read", recipientRepository.countByMessageIdAndReadAtIsNotNull(message.getId()));
            row.put("scheduledAt", message.getScheduledAt() == null ? null : message.getScheduledAt().toEpochMilli());
            row.put("sentAt", message.getSentAt() == null ? null : message.getSentAt().toEpochMilli());
            row.put("sender", message.getSenderId() == null ? "" : userRepository.findById(message.getSenderId())
                    .map(User::visibleName).orElse(""));
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<InboxAudit> auditLog() {
        return auditRepository.findTop200ByOrderByIdDesc();
    }

    /** The people a draft reaches: active accounts only, from account and purchase data only. */
    @SuppressWarnings("unchecked")
    List<Long> audience(Draft draft) {
        String kind = draft.audience() == null ? "all" : draft.audience();
        String value = draft.value() == null ? "" : draft.value().trim();
        String active = "u.deleted = false and u.status = :active";
        if ("user".equals(kind)) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("گیرنده را مشخص کن (شناسه، ایمیل یا شماره).");
            }
            Long id = value.matches("\\d{1,12}") && value.length() < 9 ? Long.valueOf(value) : null;
            return entityManager.createQuery("select u.id from User u where " + active
                            + " and (u.id = :id or u.email = :value or u.phoneNumber = :value or u.username = :value)")
                    .setParameter("active", UserStatus.ACTIVE).setParameter("id", id == null ? -1L : id)
                    .setParameter("value", value).getResultList();
        }
        if ("segment".equals(kind)) {
            String segment = draft.segment() == null ? "" : draft.segment();
            switch (segment) {
                case "plan" -> {
                    return entityManager.createQuery("select distinct u.id from User u where " + active
                                    + " and u.id in (select p.userId from SessionPackage p where p.tier = :plan)")
                            .setParameter("active", UserStatus.ACTIVE).setParameter("plan", value.toUpperCase()).getResultList();
                }
                case "free_only" -> {
                    return entityManager.createQuery("select u.id from User u where " + active
                                    + " and u.id not in (select p.userId from SessionPackage p)")
                            .setParameter("active", UserStatus.ACTIVE).getResultList();
                }
                case "expiring" -> {
                    return entityManager.createQuery("select distinct u.id from User u where " + active
                                    + " and u.id in (select p.userId from SessionPackage p where p.expiresAt > :now"
                                    + " and p.expiresAt < :soon and p.sessionsUsed < p.sessionsTotal)")
                            .setParameter("active", UserStatus.ACTIVE).setParameter("now", Instant.now())
                            .setParameter("soon", Instant.now().plus(Duration.ofDays(7))).getResultList();
                }
                case "registered_after" -> {
                    LocalDate day;
                    try {
                        day = LocalDate.parse(value);
                    } catch (RuntimeException wrong) {
                        throw new IllegalArgumentException("تاریخ را به شکل 2026-11-03 بنویس.");
                    }
                    return entityManager.createQuery("select u.id from User u where " + active + " and u.createdAt >= :from")
                            .setParameter("active", UserStatus.ACTIVE).setParameter("from", day.atStartOfDay())
                            .getResultList();
                }
                default -> throw new IllegalArgumentException("گروه گیرنده نامعتبر است.");
            }
        }
        return entityManager.createQuery("select u.id from User u where " + active)
                .setParameter("active", UserStatus.ACTIVE).getResultList();
    }

    private void audit(Long actorId, String action, Long messageId, String details, String ip) {
        InboxAudit row = new InboxAudit();
        row.setActorId(actorId);
        row.setAction(action);
        row.setMessageId(messageId);
        row.setDetails(details == null ? null : cut(details, 300));
        row.setIp(ip == null ? null : cut(ip, 64));
        auditRepository.save(row);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String cut(String text, int max) {
        String value = text == null ? "" : text;
        return value.length() > max ? value.substring(0, max) : value;
    }

    static ZoneId tehran() {
        return TEHRAN;
    }
}
