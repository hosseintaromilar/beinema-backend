package com.coupleai.coupleai.beinema.mail;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.inbox.InboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Email from the admin panel: to one person, or to a group (the same groups as the inbox) or
 * everyone. The admin chooses the sender: no-reply, support or info at beinema.ir. A group email
 * carries a one-click way to stop group emails (List-Unsubscribe, as Gmail expects), skips those
 * who used it, and goes out one by one, a few seconds apart, in the background.
 */
@Service
public class AdminEmailService {

    private static final Logger log = LoggerFactory.getLogger(AdminEmailService.class);
    private static final long GAP_MILLIS = 3000;

    /** The senders the admin can choose: address part and the name people see. */
    public static final Map<String, String[]> SENDERS = Map.of(
            "no-reply", new String[]{"no-reply", "بین‌ما"},
            "support", new String[]{"support", "پشتیبانی بین‌ما"},
            "info", new String[]{"info", "بین‌ما"});

    /** What the admin fills in. kind: single or group; audience, segment, value: as in the inbox. */
    public record Draft(String fromKey, String subject, String body, String kind, String audience, String segment,
                        String value) {
    }

    private final SmtpMailer mailer;
    private final InboxService inboxService;
    private final UserRepository userRepository;
    private final EmailCampaignRepository campaignRepository;
    private final byte[] secret;
    private final String siteUrl;
    private final String replyTo;

    public AdminEmailService(SmtpMailer mailer, InboxService inboxService, UserRepository userRepository,
                             EmailCampaignRepository campaignRepository, @Value("${jwt.secret}") String secret,
                             @Value("${beinema.site-url:https://beinema.ir}") String siteUrl,
                             @Value("${beinema.mail.reply-to:}") String replyTo) {
        this.mailer = mailer;
        this.inboxService = inboxService;
        this.userRepository = userRepository;
        this.campaignRepository = campaignRepository;
        this.secret = ("email-unsubscribe:" + secret).getBytes(StandardCharsets.UTF_8);
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.replyTo = replyTo;
    }

    /** How many of the chosen people have an address, and how many will really get it. */
    public Map<String, Integer> preview(Draft draft) {
        List<User> people = people(draft);
        int reachable = 0;
        for (User user : people) {
            if (address(user) != null && !(group(draft) && Boolean.TRUE.equals(user.getEmailOptOut()))) {
                reachable++;
            }
        }
        return Map.of("people", people.size(), "count", reachable);
    }

    /** Records the email and sends it in the background; the panel follows the counts. */
    public EmailCampaign send(User admin, Draft draft) {
        if (!mailer.configured()) {
            throw new IllegalArgumentException(EmailAuthService.NOT_READY);
        }
        String subject = required(draft.subject(), "موضوع", 150);
        String body = required(draft.body(), "متن", 8000);
        String fromKey = SENDERS.containsKey(draft.fromKey()) ? draft.fromKey() : "no-reply";
        boolean group = group(draft);
        if (group && campaignRepository.existsByKindAndStatus("group", EmailCampaign.SENDING)) {
            throw new IllegalArgumentException("یک ایمیل گروهی هنوز در حال ارسال است؛ بعد از تمام شدنش بفرست.");
        }
        List<User> people = people(draft);
        if (people.isEmpty()) {
            throw new IllegalArgumentException("کسی با این مشخصات پیدا نشد.");
        }
        EmailCampaign campaign = new EmailCampaign();
        campaign.setSenderId(admin.getId());
        campaign.setFromKey(fromKey);
        campaign.setSubject(subject);
        campaign.setBody(body);
        campaign.setKind(group ? "group" : "single");
        campaign.setAudienceFilter(cut((draft.audience() == null ? "all" : draft.audience()) + ":"
                + (draft.segment() == null ? "" : draft.segment()) + ":" + (draft.value() == null ? "" : draft.value()), 300));
        campaign.setStatus(EmailCampaign.SENDING);
        campaign.setRecipients(people.size());
        EmailCampaign saved = campaignRepository.save(campaign);
        List<Long> ids = people.stream().map(User::getId).toList();
        Thread thread = new Thread(() -> deliver(saved.getId(), ids), "beinema-email-" + saved.getId());
        thread.setDaemon(true);
        thread.start();
        return saved;
    }

    public List<Map<String, Object>> history() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmailCampaign campaign : campaignRepository.findTop100ByOrderByIdDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", campaign.getId());
            row.put("subject", campaign.getSubject());
            row.put("from", campaign.getFromKey() + "@beinema.ir");
            row.put("kind", campaign.getKind());
            row.put("audience", campaign.getAudienceFilter());
            row.put("status", campaign.getStatus());
            row.put("recipients", campaign.getRecipients());
            row.put("sent", campaign.getSent());
            row.put("failed", campaign.getFailed());
            row.put("skipped", campaign.getSkipped());
            row.put("createdAt", campaign.getCreatedAt() == null ? null : campaign.getCreatedAt().toString());
            rows.add(row);
        }
        return rows;
    }

    /** The link in a group email: no more group emails for this person. */
    public boolean unsubscribe(Long userId, String token) {
        if (userId == null || token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.US_ASCII), token(userId).getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setEmailOptOut(true);
            userRepository.save(user);
        });
        return true;
    }

    // ------------------------------------------------------------------ inside

    private void deliver(Long campaignId, List<Long> userIds) {
        EmailCampaign campaign = campaignRepository.findById(campaignId).orElseThrow();
        boolean group = "group".equals(campaign.getKind());
        String[] sender = SENDERS.get(campaign.getFromKey());
        String fromAddress = sender[0] + "@beinema.ir";
        // replies to no-reply go nowhere; to support and info, to the mailbox set for replies, if any
        String reply = "no-reply".equals(campaign.getFromKey()) || replyTo.isBlank() ? null : replyTo;
        int count = 0;
        for (Long userId : userIds) {
            User user = userRepository.findById(userId).orElse(null);
            String to = user == null ? null : address(user);
            if (to == null || (group && Boolean.TRUE.equals(user.getEmailOptOut()))) {
                campaign.setSkipped(campaign.getSkipped() + 1);
                campaignRepository.save(campaign);
                continue;
            }
            Map<String, String> headers = new LinkedHashMap<>();
            String text = greeting(user) + campaign.getBody();
            String html;
            if (group) {
                String link = siteUrl + "/api/email/unsubscribe?u=" + userId + "&t=" + token(userId);
                headers.put("List-Unsubscribe", "<" + link + ">");
                headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
                text = text + "\n\n—\nبرای اینکه دیگر ایمیل گروهی نگیری: " + link;
                html = MailLetter.html(greeting(user) + campaign.getBody(), link);
            } else {
                html = MailLetter.html(text, null);
            }
            try {
                mailer.send(new SmtpMailer.Mail(to, fromAddress, sender[1], campaign.getSubject(), text, html, reply, headers));
                campaign.setSent(campaign.getSent() + 1);
            } catch (RuntimeException failure) {
                campaign.setFailed(campaign.getFailed() + 1);
                log.warn("Email {} to user {} failed: {}", campaignId, userId, failure.getMessage());
            }
            campaignRepository.save(campaign);
            if (group && ++count < userIds.size()) {
                try {
                    Thread.sleep(GAP_MILLIS);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        campaign.setStatus(EmailCampaign.DONE);
        campaign.setFinishedAt(Instant.now());
        campaignRepository.save(campaign);
        log.info("Email {} done: {} sent, {} failed, {} skipped", campaignId, campaign.getSent(), campaign.getFailed(), campaign.getSkipped());
    }

    private List<User> people(Draft draft) {
        InboxService.Draft target = new InboxService.Draft("announcement", "-", "-", null, null, null,
                group(draft) ? (draft.audience() == null ? "all" : draft.audience()) : "user",
                draft.segment(), draft.value(), null, null);
        return userRepository.findAllById(inboxService.audience(target));
    }

    private static boolean group(Draft draft) {
        return !"single".equals(draft.kind());
    }

    /** The address to write to: the one the person gave, or the account's email if it is a real one. */
    static String address(User user) {
        if (user.getContactEmail() != null && !user.getContactEmail().isBlank()) {
            return user.getContactEmail();
        }
        String email = user.getEmail();
        if (email == null || !email.contains("@") || email.endsWith("@users.beinema") || email.endsWith(".local")
                || email.startsWith("released-")) {
            return null;
        }
        return email;
    }

    private static String greeting(User user) {
        String name = user.getFirstName() != null && !user.getFirstName().isBlank() ? user.getFirstName()
                : user.hasRegisteredName() ? user.getName() : null;
        return name == null ? "سلام،\n\n" : "سلام " + name.trim() + "،\n\n";
    }

    private String token(Long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(String.valueOf(userId).getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String required(String text, String label, int max) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " را بنویس.");
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(label + " بیشتر از " + max + " نویسه است.");
        }
        return value;
    }

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
