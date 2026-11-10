package com.coupleai.coupleai.beinema.mail;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.support.SupportService;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The emails that reach support@ and info@. The mail-in service (Postfix, deploy/docker-compose.yml)
 * accepts mail only for those two addresses and leaves each one as a file in a Maildir; every 30
 * seconds this reads the new files, turns each into a ticket message (SupportService.fromEmail) and
 * removes the file. Only the text is kept, without the quoted earlier mail and without attachments.
 * Mail from our own domain, automatic replies and more than a few emails an hour from one address
 * are dropped. Off when MAIL_INBOUND_DIR is not set.
 */
@Service
public class InboundMailService {

    private static final Logger log = LoggerFactory.getLogger(InboundMailService.class);
    private static final List<String> MAILBOXES = List.of("support", "info");
    static final int PER_SENDER_PER_HOUR = 6;

    private final SupportService supportService;
    private final UserRepository userRepository;
    private final String inboundDir;
    private final Session session = Session.getInstance(new Properties());
    private final Map<String, Deque<Instant>> recent = new HashMap<>();

    public InboundMailService(SupportService supportService, UserRepository userRepository,
                              @Value("${beinema.mail.inbound-dir:}") String inboundDir) {
        this.supportService = supportService;
        this.userRepository = userRepository;
        this.inboundDir = inboundDir;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void readNew() {
        if (inboundDir == null || inboundDir.isBlank()) {
            return;
        }
        for (String mailbox : MAILBOXES) {
            Path fresh = Path.of(inboundDir, mailbox, "new");
            if (!Files.isDirectory(fresh)) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(fresh)) {
                for (Path file : files) {
                    handle(mailbox, file);
                }
            } catch (IOException failure) {
                log.warn("Inbound mail folder {} could not be read: {}", mailbox, failure.getMessage());
            }
        }
    }

    private void handle(String mailbox, Path file) {
        try {
            String outcome = read(mailbox, file);
            Files.deleteIfExists(file);
            log.info("Inbound mail to {}: {}", mailbox, outcome);
        } catch (Exception failure) {
            // kept aside, so one bad email does not stop the others
            log.warn("Inbound mail {} to {} could not be read: {}", file.getFileName(), mailbox, failure.getMessage());
            try {
                Path aside = Path.of(inboundDir, "failed");
                Files.createDirectories(aside);
                Files.move(file, aside.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // tried
            }
        }
    }

    /** One email: dropped (with the reason) or added to a ticket. */
    String read(String mailbox, Path file) throws Exception {
        MimeMessage message;
        try (InputStream in = Files.newInputStream(file)) {
            message = new MimeMessage(session, in);
        }
        InternetAddress[] from = (InternetAddress[]) message.getFrom();
        if (from == null || from.length == 0 || from[0].getAddress() == null) {
            return "dropped: no sender";
        }
        String sender = from[0].getAddress().trim().toLowerCase(Locale.ROOT);
        if (!EmailAddresses.valid(sender) || sender.endsWith("@beinema.ir")) {
            return "dropped: our own or an invalid address";
        }
        if (automatic(message)) {
            return "dropped: an automatic reply";
        }
        if (!allowed(sender)) {
            return "dropped: too many from one address";
        }
        String text = withoutQuote(textOf(message));
        if (text.isBlank()) {
            text = "(ایمیل بدون متن)";
        }
        if (hasAttachment(message)) {
            text += "\n\n(این ایمیل پیوست داشت که در پنل نشان داده نمی‌شود.)";
        }
        Long userId = account(sender);
        Long ticketId = supportService.fromEmail(userId, sender, mailbox, message.getSubject(), text, message.getMessageID());
        return "ticket " + ticketId + (userId == null ? " (unknown address)" : " (user " + userId + ")");
    }

    /** Out-of-office answers and mailing lists: never a ticket, and never answered (no loops). */
    private static boolean automatic(MimeMessage message) throws Exception {
        String auto = message.getHeader("Auto-Submitted", null);
        String precedence = message.getHeader("Precedence", null);
        return (auto != null && !auto.trim().equalsIgnoreCase("no"))
                || (precedence != null && precedence.toLowerCase(Locale.ROOT).matches(".*(bulk|list|junk|auto_reply).*"))
                || message.getHeader("X-Autoreply", null) != null
                || message.getHeader("List-Id", null) != null;
    }

    private synchronized boolean allowed(String sender) {
        Deque<Instant> times = recent.computeIfAbsent(sender, key -> new ArrayDeque<>());
        Instant hourAgo = Instant.now().minusSeconds(3600);
        while (!times.isEmpty() && times.peekFirst().isBefore(hourAgo)) {
            times.pollFirst();
        }
        if (times.size() >= PER_SENDER_PER_HOUR) {
            return false;
        }
        times.addLast(Instant.now());
        if (recent.size() > 5000) {
            recent.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        return true;
    }

    private Long account(String sender) {
        String normalized = EmailAddresses.normalize(sender);
        return userRepository.findByEmailNormalized(normalized)
                .or(() -> userRepository.findByEmail(sender))
                .filter(user -> !user.isDeleted())
                .map(User::getId)
                .orElse(null);
    }

    /** The plain text of the email; from the page version when there is no plain text. */
    static String textOf(Part part) throws Exception {
        if (part.isMimeType("text/plain") && !Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            return String.valueOf(part.getContent());
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            String html = null;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                if (child.isMimeType("text/plain") || child.isMimeType("multipart/*")) {
                    String text = textOf(child);
                    if (!text.isBlank()) {
                        return text;
                    }
                } else if (child.isMimeType("text/html") && html == null) {
                    html = String.valueOf(child.getContent());
                }
            }
            return html == null ? "" : fromHtml(html);
        }
        if (part.isMimeType("text/html")) {
            return fromHtml(String.valueOf(part.getContent()));
        }
        return "";
    }

    private static boolean hasAttachment(Part part) throws Exception {
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            return true;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                if (hasAttachment(multipart.getBodyPart(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    static String fromHtml(String html) {
        return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    /** Cuts the earlier mail a reply quotes: from «On … wrote:» (or its Persian form) or the first «>» line. */
    static String withoutQuote(String text) {
        StringBuilder kept = new StringBuilder();
        for (String line : text.replace("\r", "").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(">")
                    || trimmed.matches("(?i)^on .{4,200} wrote:$")
                    || trimmed.matches("^.{0,200}(نوشت|نوشته است)\\s*:$")
                    || trimmed.matches("(?i)^-{2,}\\s*original message\\s*-{2,}$")) {
                break;
            }
            kept.append(line).append('\n');
        }
        return kept.toString().strip().replaceAll("\n{3,}", "\n\n");
    }
}
