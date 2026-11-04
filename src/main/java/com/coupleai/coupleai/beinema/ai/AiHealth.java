package com.coupleai.coupleai.beinema.ai;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Whether the AI service works, for everyone at once (a circuit breaker). One NO_CREDIT, or five
 * failures of the service in a minute, opens it: the site says the service is down, rooms are not
 * made and running sessions stand still. A small test call every 30 seconds, or any real call that
 * succeeds, closes it again; then the stopped sessions get their lost time back.
 */
@Component
public class AiHealth {

    private static final Logger log = LoggerFactory.getLogger(AiHealth.class);

    public static final String OK = "ok";
    public static final String DEGRADED = "degraded";
    public static final String DOWN = "down";

    static final int FAILURES_TO_OPEN = 5;
    static final Duration WINDOW = Duration.ofSeconds(60);
    static final int RETRY_AFTER_SECONDS = 30;
    private static final Duration BAD_REQUEST_ALERT_GAP = Duration.ofMinutes(10);

    private final ChatRoomRepository chatRoomRepository;
    private final AiIncidentRepository incidentRepository;
    private final AiSessionPauseRepository pauseRepository;
    private final AdminAlertRepository alertRepository;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${metis.base-url}")
    private String baseUrl;

    @Value("${metis.api-key}")
    private String apiKey;

    private final Deque<Instant> recentFailures = new ArrayDeque<>();
    private boolean open;
    private Instant since;
    private AiError cause;
    private Long incidentId;
    private Instant lastBadRequestAlert;
    /** Rooms whose session was running when the service went down, with that session's number. */
    private final Map<Long, Integer> stoppedSessions = new LinkedHashMap<>();

    /** Mail for the admin when the service goes down for want of credit or errors, and when it is back. */
    private com.coupleai.coupleai.beinema.mail.SmtpMailer mailer;

    @org.springframework.beans.factory.annotation.Value("${beinema.support.notify-email:}")
    private String notifyEmail;

    /** At most one «down» email an hour, though the service may go up and down many times. */
    private Instant lastDownMail;
    private boolean downMailed;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMailer(com.coupleai.coupleai.beinema.mail.SmtpMailer mailer) {
        this.mailer = mailer;
    }

    public AiHealth(ChatRoomRepository chatRoomRepository, AiIncidentRepository incidentRepository,
                    AiSessionPauseRepository pauseRepository, AdminAlertRepository alertRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.incidentRepository = incidentRepository;
        this.pauseRepository = pauseRepository;
        this.alertRepository = alertRepository;
    }

    public record Status(String ai, Long since, int retryAfterSec) {
    }

    public synchronized boolean down() {
        return open;
    }

    public synchronized Status status() {
        if (open) {
            return new Status(DOWN, since.toEpochMilli(), RETRY_AFTER_SECONDS);
        }
        prune(Instant.now());
        return new Status(recentFailures.isEmpty() ? OK : DEGRADED, null, 0);
    }

    /** A call that worked: the service is back, if it was down. */
    public void succeeded() {
        boolean wasOpen;
        synchronized (this) {
            wasOpen = open;
        }
        if (wasOpen) {
            close("a call succeeded");
        }
    }

    /** A call that failed; returns its kind. */
    public AiError failed(Throwable failure) {
        AiError error = AiError.of(failure);
        Instant now = Instant.now();
        boolean openNow = false;
        boolean alertBadRequest = false;
        synchronized (this) {
            if (error == AiError.BAD_REQUEST) {
                if (lastBadRequestAlert == null || lastBadRequestAlert.plus(BAD_REQUEST_ALERT_GAP).isBefore(now)) {
                    lastBadRequestAlert = now;
                    alertBadRequest = true;
                }
            } else if (error != AiError.RATE_LIMIT) {
                recentFailures.addLast(now);
                prune(now);
                if (!open && (error == AiError.NO_CREDIT || recentFailures.size() >= FAILURES_TO_OPEN)) {
                    open = true;
                    since = now;
                    cause = error;
                    openNow = true;
                }
            }
        }
        if (alertBadRequest) {
            alert(AdminAlert.BAD_REQUEST, "یک درخواست به سرویس هوش مصنوعی رد شد: " + shortText(failure));
        }
        if (openNow) {
            opened(error, now);
        }
        return error;
    }

    /** The admin's button: back to normal now (for example right after charging the account). */
    public void reset() {
        close("closed by the admin");
    }

    @Scheduled(fixedDelay = RETRY_AFTER_SECONDS * 1000L)
    public void probe() {
        if (!down() || apiKey == null || apiKey.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/wrapper/deepseek/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"model\":\"deepseek-v4-1-flash-1017\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"ok\"}]}"))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (AiError.ofStatus(response.statusCode(), response.body()) == null) {
                close("the test call succeeded");
            }
        } catch (java.io.IOException failure) {
            // still down
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private void opened(AiError error, Instant now) {
        log.warn("AI service marked down: {}", error);
        Map<Long, Integer> running = new LinkedHashMap<>();
        for (ChatRoom room : chatRoomRepository.findRunning(ChatRoomStatus.ACTIVE)) {
            if (room.getSessionStartedAt() != null && !Boolean.FALSE.equals(room.getSessionActive())) {
                running.put(room.getId(), room.getSessionNumber() == null ? 1 : room.getSessionNumber());
            }
        }
        AiIncident incident = new AiIncident();
        incident.setStartedAt(now);
        incident.setErrorCode(error.name());
        incident.setAffectedSessions(running.size());
        incident = incidentRepository.save(incident);
        synchronized (this) {
            stoppedSessions.clear();
            stoppedSessions.putAll(running);
            incidentId = incident.getId();
        }
        alert(error == AiError.NO_CREDIT ? AdminAlert.NO_CREDIT : AdminAlert.ERROR_SPIKE,
                error == AiError.NO_CREDIT
                        ? "اعتبار سرویس هوش مصنوعی تمام شده است. همه ایجنت‌ها متوقف‌اند تا حساب شارژ شود."
                        : "سرویس هوش مصنوعی پشت سر هم خطا داد و موقتاً قطع اعلام شد.");
    }

    private void close(String why) {
        Instant from;
        Instant now = Instant.now();
        Map<Long, Integer> stopped;
        Long incident;
        AiError was;
        synchronized (this) {
            if (!open) {
                return;
            }
            open = false;
            from = since;
            was = cause;
            since = null;
            cause = null;
            recentFailures.clear();
            stopped = new LinkedHashMap<>(stoppedSessions);
            stoppedSessions.clear();
            incident = incidentId;
            incidentId = null;
        }
        Duration lost = Duration.between(from, now);
        log.info("AI service back after {} s ({})", lost.toSeconds(), why);
        int moved = 0;
        for (Map.Entry<Long, Integer> entry : stopped.entrySet()) {
            try {
                if (giveTimeBack(entry.getKey(), entry.getValue(), from, now, was)) {
                    moved++;
                }
            } catch (RuntimeException failure) {
                log.warn("Could not give lost time back to room {}: {}", entry.getKey(), failure.getMessage());
            }
        }
        if (incident != null) {
            final int sessions = moved;
            incidentRepository.findById(incident).ifPresent(row -> {
                row.setEndedAt(now);
                row.setNotes(why + "; " + sessions + " sessions got their time back");
                incidentRepository.save(row);
            });
        }
        alert(AdminAlert.RECOVERED, "سرویس هوش مصنوعی دوباره کار می‌کند؛ مدت قطعی: " + Math.max(1, lost.toMinutes()) + " دقیقه.");
    }

    /** The session's clock starts that much later, so the outage is not counted. */
    private boolean giveTimeBack(Long roomId, Integer number, Instant from, Instant to, AiError was) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null || room.getSessionStartedAt() == null || Boolean.FALSE.equals(room.getSessionActive())
                || !number.equals(room.getSessionNumber() == null ? 1 : room.getSessionNumber())) {
            return false;
        }
        room.setSessionStartedAt(room.getSessionStartedAt().plus(Duration.between(from, to)));
        chatRoomRepository.save(room);
        AiSessionPause pause = new AiSessionPause();
        pause.setChatRoomId(roomId);
        pause.setSessionNumber(number);
        pause.setPausedAt(from);
        pause.setResumedAt(to);
        pause.setReasonCode(was == null ? AiError.UPSTREAM.name() : was.name());
        pauseRepository.save(pause);
        return true;
    }

    /** How long a session stood still for outages, in seconds. */
    public long pausedSeconds(Long roomId, Integer number) {
        return pauseRepository.findAllByChatRoomIdAndSessionNumber(roomId, number).stream()
                .mapToLong(pause -> Duration.between(pause.getPausedAt(), pause.getResumedAt()).toSeconds())
                .sum();
    }

    private void alert(String kind, String detail) {
        try {
            AdminAlert alert = new AdminAlert();
            alert.setKind(kind);
            alert.setDetail(detail.length() > 300 ? detail.substring(0, 300) : detail);
            alertRepository.save(alert);
        } catch (RuntimeException failure) {
            log.warn("Could not save the admin alert {}: {}", kind, failure.getMessage());
        }
        mailAdmin(kind, detail);
    }

    private void mailAdmin(String kind, String detail) {
        if (mailer == null || notifyEmail == null || notifyEmail.isBlank() || !mailer.configured()) {
            return;
        }
        boolean noCredit = AdminAlert.NO_CREDIT.equals(kind);
        boolean down = noCredit || AdminAlert.ERROR_SPIKE.equals(kind);
        boolean back = AdminAlert.RECOVERED.equals(kind);
        Instant now = Instant.now();
        if ((!down && !back) || (back && !downMailed)
                || (down && lastDownMail != null && lastDownMail.isAfter(now.minus(Duration.ofHours(1))))) {
            return;
        }
        if (down) {
            lastDownMail = now;
        }
        downMailed = down;
        String subject = noCredit ? "شارژ متیس تمام شد؛ همراه‌های بین‌ما متوقف‌اند"
                : down ? "سرویس هوش مصنوعی بین‌ما خطا می‌دهد" : "سرویس هوش مصنوعی بین‌ما دوباره کار می‌کند";
        String body = detail + "\n\n"
                + (noCredit ? "حساب متیس را شارژ کن. بعد از شارژ، سرویس خودش دوباره امتحان می‌کند؛ اگر خواستی زودتر "
                + "برگردد، در پنل «سرویس هوش مصنوعی» دکمهٔ «بستن مدار» را بزن.\n\n" : "")
                + "https://admin.beinema.ir/ai";
        String to = notifyEmail.trim();
        Thread thread = new Thread(() -> {
            try {
                mailer.send(to, subject, body);
            } catch (RuntimeException failure) {
                log.warn("The admin was not mailed about {}: {}", kind, failure.getMessage());
            }
        }, "beinema-ai-alert");
        thread.setDaemon(true);
        thread.start();
    }

    private void prune(Instant now) {
        while (!recentFailures.isEmpty() && recentFailures.peekFirst().isBefore(now.minus(WINDOW))) {
            recentFailures.removeFirst();
        }
    }

    private static String shortText(Throwable failure) {
        String text = String.valueOf(failure.getMessage());
        // only the status line: a request body could hold someone's words
        int cut = text.indexOf(':', text.indexOf("HTTP") + 1);
        text = cut > 0 ? text.substring(0, cut) : text;
        return text.length() > 120 ? text.substring(0, 120) : text;
    }
}
