package com.coupleai.coupleai.beinema.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends a plain-text email over SMTP, with no library. Two ways:
 * <ul>
 *   <li>our own Postfix next to the backend (the `mail` service in deploy/docker-compose.yml):
 *       MAIL_HOST=mail, MAIL_PORT=587, MAIL_TLS=false, MAIL_FROM=no-reply@beinema.ir and no
 *       user name; it stays inside the Docker network, so no password or TLS;</li>
 *   <li>a mail provider over TLS from the first byte (port 465) with a user name and password,
 *       for example a Google account with an app password.</li>
 * </ul>
 * Without a sender address, email is simply not offered.
 */
@Component
public class SmtpMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);

    @Value("${beinema.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${beinema.mail.port:465}")
    private int port;

    /** TLS from the first byte; off only for the Postfix inside the Docker network. */
    @Value("${beinema.mail.tls:true}")
    private boolean tls;

    @Value("${beinema.mail.username:}")
    private String username;

    @Value("${beinema.mail.password:}")
    private String password;

    /** The address people see; with Gmail it must be the account itself or an alias of it. */
    @Value("${beinema.mail.from:}")
    private String from;

    @Value("${beinema.mail.from-name:بین‌ما}")
    private String fromName;

    public boolean configured() {
        boolean login = username != null && !username.isBlank();
        boolean sender = login || (from != null && !from.isBlank());
        // a user name needs its password; without one the server must take mail as it is
        return sender && (login ? password != null && !password.isBlank() : !tls);
    }

    /**
     * One message. fromAddress and fromName empty: the default sender. html empty: made from the
     * text. headers: extra ones, such as List-Unsubscribe.
     */
    public record Mail(String to, String fromAddress, String fromName, String subject, String text, String html,
                       String replyTo, Map<String, String> headers) {
    }

    /** Sends one plain message from the default sender, with an HTML copy of the same words. */
    public void send(String to, String subject, String body) {
        send(new Mail(to, null, null, subject, body, null, null, Map.of()));
    }

    /** Sends one message; throws when the mail server refused it. */
    public void send(Mail mail) {
        if (!configured()) {
            throw new IllegalStateException("mail is not configured");
        }
        String to = mail.to();
        String sender = mail.fromAddress() != null && !mail.fromAddress().isBlank() ? mail.fromAddress()
                : from == null || from.isBlank() ? username : from;
        String name = mail.fromName() != null && !mail.fromName().isBlank() ? mail.fromName() : fromName;
        String html = mail.html() != null && !mail.html().isBlank() ? mail.html() : MailLetter.html(mail.text(), null);
        try (Socket socket = tls ? SSLSocketFactory.getDefault().createSocket(host, port) : new Socket(host, port)) {
            socket.setSoTimeout(20_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            OutputStream out = socket.getOutputStream();
            expect(in, 220);
            command(out, in, "EHLO beinema.ir", 250);
            if (username != null && !username.isBlank()) {
                command(out, in, "AUTH LOGIN", 334);
                command(out, in, base64(username), 334);
                command(out, in, base64(password), 235);
            }
            command(out, in, "MAIL FROM:<" + sender + ">", 250);
            command(out, in, "RCPT TO:<" + to + ">", 250);
            command(out, in, "DATA", 354);
            // the words as plain text and as a page, side by side: mail programs show the one they can
            String boundary = "beinema-" + UUID.randomUUID();
            Map<String, String> head = new LinkedHashMap<>();
            head.put("From", encoded(name) + " <" + sender + ">");
            head.put("To", "<" + to + ">");
            if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
                head.put("Reply-To", "<" + mail.replyTo() + ">");
            }
            head.put("Subject", encoded(mail.subject()));
            head.put("Date", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
            head.put("Message-ID", "<" + UUID.randomUUID() + "@beinema.ir>");
            if (mail.headers() != null) {
                head.putAll(mail.headers());
            }
            head.put("MIME-Version", "1.0");
            head.put("Content-Type", "multipart/alternative; boundary=\"" + boundary + "\"");
            StringBuilder message = new StringBuilder();
            head.forEach((key, value) -> message.append(key).append(": ").append(value).append("\r\n"));
            message.append("\r\n")
                    .append(part(boundary, "text/plain", mail.text()))
                    .append(part(boundary, "text/html", html))
                    .append("--").append(boundary).append("--\r\n.");
            command(out, in, message.toString(), 250);
            write(out, "QUIT");
        } catch (IOException failure) {
            log.warn("Mail to a {} address failed: {}", to.substring(to.indexOf('@') + 1), failure.getMessage());
            throw new IllegalStateException("mail failed", failure);
        }
    }

    private static String part(String boundary, String type, String content) {
        return "--" + boundary + "\r\n"
                + "Content-Type: " + type + "; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n"
                + Base64.getMimeEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)) + "\r\n";
    }

    private static void command(OutputStream out, BufferedReader in, String line, int code) throws IOException {
        write(out, line);
        expect(in, code);
    }

    private static void write(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Reads a reply (all its lines) and checks its code. */
    private static void expect(BufferedReader in, int code) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) {
                throw new IOException("the mail server closed the connection");
            }
        } while (line.length() > 3 && line.charAt(3) == '-');
        if (!line.startsWith(String.valueOf(code))) {
            // never the whole reply: it could echo an address
            throw new IOException("mail server answered " + line.substring(0, Math.min(3, line.length())));
        }
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String encoded(String text) {
        return "=?UTF-8?B?" + base64(text) + "?=";
    }
}
