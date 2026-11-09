package com.coupleai.coupleai.beinema.mirror;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.RelationshipInsight;
import com.coupleai.coupleai.beinema.Entity.SessionRecord;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomMemberStatus;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.SessionRecordRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Service.RelationshipInsightService;
import com.coupleai.coupleai.beinema.mail.AdminEmailService;
import com.coupleai.coupleai.beinema.mail.SmtpMailer;
import com.coupleai.coupleai.beinema.market.MarketDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The nightly mirror. At 22:00 Tehran time, every conversation that had a session (with something
 * said in it) since 22:00 the night before gets its mirror for the day: the one written when its
 * last session closed, or written now if that failed. It is kept for the app (the room's panel and
 * the dashboard card) and sent to both people by email, unless they turned that off.
 * <p>
 * The mirror is shared, so it is made only from what both people saw in the room (never a private
 * message to the companion or a safety note shown to one of them; see RelationshipInsightService).
 * A report that could not be written is tried again every quarter hour, up to MAX_ATTEMPTS times.
 */
@Service
public class MirrorReportService {

    private static final Logger log = LoggerFactory.getLogger(MirrorReportService.class);
    static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    static final LocalTime CUTOFF = LocalTime.of(22, 0);
    static final int MAX_ATTEMPTS = 6;

    static final Map<String, String[]> MOODS = Map.of(
            "starting", new String[]{"تازه شروع شده", "هنوز در حال شکل گرفتن مسیر مشترک هستید."},
            "softer", new String[]{"نرم‌تر شده", "چرخهٔ قبلی کمی آرام‌تر شده است."},
            "same", new String[]{"همان چرخه", "الگوی تکراری هنوز پابرجاست؛ این به معنی مقصر بودن کسی نیست."},
            "returned", new String[]{"برگشته", "یک الگوی آشنا دوباره دیده می‌شود."});

    private static final String[] MONTHS = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};

    private final MirrorReportRepository reports;
    private final SessionRecordRepository sessions;
    private final ChatRoomRepository rooms;
    private final ChatRoomParticipantRepository participants;
    private final UserRepository users;
    private final RelationshipInsightService insights;
    private final SmtpMailer mailer;
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final String siteUrl;
    private final TransactionTemplate readOnly;

    public MirrorReportService(MirrorReportRepository reports, SessionRecordRepository sessions, ChatRoomRepository rooms,
                               ChatRoomParticipantRepository participants, UserRepository users,
                               RelationshipInsightService insights, SmtpMailer mailer, ObjectMapper objectMapper,
                               PlatformTransactionManager transactions,
                               @Value("${jwt.secret}") String secret,
                               @Value("${beinema.site-url:https://beinema.ir}") String siteUrl) {
        this.reports = reports;
        this.sessions = sessions;
        this.rooms = rooms;
        this.participants = participants;
        this.users = users;
        this.insights = insights;
        this.mailer = mailer;
        this.objectMapper = objectMapper;
        this.secret = ("mirror-email-off:" + secret).getBytes(StandardCharsets.UTF_8);
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    // ------------------------------------------------------------------ the night job

    /** The last 22:00 Tehran time that has passed. */
    static LocalDateTime lastCutoff(LocalDateTime nowInTehran) {
        LocalDateTime today = nowInTehran.toLocalDate().atTime(CUTOFF);
        return nowInTehran.isBefore(today) ? today.minusDays(1) : today;
    }

    /** Every quarter hour: once 22:00 has passed, the day's mirrors that are not written yet. */
    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Tehran")
    public void nightly() {
        LocalDateTime cutoff = lastCutoff(LocalDateTime.now(TEHRAN));
        Instant to = cutoff.atZone(TEHRAN).toInstant();
        Instant from = cutoff.minusDays(1).atZone(TEHRAN).toInstant();
        LocalDate day = cutoff.toLocalDate();

        Map<Long, Integer> perRoom = new LinkedHashMap<>();
        for (SessionRecord session : sessions.findAllByEndedAtGreaterThanAndEndedAtLessThanEqualAndCompletedTrue(from, to)) {
            perRoom.merge(session.getChatRoomId(), 1, Integer::sum);
        }
        perRoom.forEach((roomId, count) -> {
            try {
                write(roomId, day, count, from);
            } catch (RuntimeException failure) {
                log.warn("Mirror of room {} for {} failed: {}", roomId, day, failure.getMessage());
            }
        });
    }

    private void write(Long roomId, LocalDate day, int sessionsCount, Instant since) {
        MirrorReport report = reports.findByChatRoomIdAndDay(roomId, day).orElseGet(() -> {
            MirrorReport fresh = new MirrorReport();
            fresh.setChatRoomId(roomId);
            fresh.setDay(day);
            return fresh;
        });
        if (!MirrorReport.PENDING.equals(report.getStatus()) || report.getAttempts() >= MAX_ATTEMPTS) {
            return;
        }
        ChatRoom room = rooms.findById(roomId).orElse(null);
        if (room == null) {
            return;
        }
        report.setSessionsCount(sessionsCount);
        report.setAttempts(report.getAttempts() + 1);
        RelationshipInsight insight;
        try {
            insight = insights.freshFor(roomId, since);
        } catch (RuntimeException failure) {
            report.setLastError(trim(failure.getMessage(), 300));
            if (report.getAttempts() >= MAX_ATTEMPTS) {
                report.setStatus(MirrorReport.FAILED);
            }
            reports.save(report);
            return;
        }
        if (insight == null) {
            report.setStatus(MirrorReport.SKIPPED);
            reports.save(report);
            return;
        }
        report.setMood(insight.getStatus());
        report.setWhereWeWere(insight.getWhereWeWere());
        report.setWhereWeAre(insight.getWhereWeAre());
        report.setNeedsJson(insight.getNeedsJson());
        report.setPatternsJson(insight.getPatternsJson());
        report.setNextStep(insight.getNextStep());
        report.setDailySentence(insight.getDailySentence());
        report.setBuiltAt(Instant.now());
        report.setStatus(MirrorReport.DONE);
        report.setLastError(null);
        report = reports.save(report);
        MirrorReport written = report;
        // the members and their users load lazily: read them inside a transaction
        Integer emailed = readOnly.execute(status -> email(rooms.findById(roomId).orElse(room), written));
        report.setEmailed(emailed == null ? 0 : emailed);
        reports.save(report);
        log.info("Mirror of room {} for {} written; emailed to {}", roomId, day, report.getEmailed());
    }

    /** To each active member with an email address who has not turned this off. */
    private int email(ChatRoom room, MirrorReport report) {
        if (!mailer.configured()) {
            return 0;
        }
        int sent = 0;
        for (ChatRoomParticipant person : participants.findAllByChatRoom(room)) {
            User user = person.getUser();
            if (user == null || person.getMemberStatus() == ChatRoomMemberStatus.LEFT
                    || person.getMemberStatus() == ChatRoomMemberStatus.REMOVED
                    || Boolean.TRUE.equals(user.getMirrorEmailOff())) {
                continue;
            }
            String to = AdminEmailService.address(user);
            if (to == null) {
                continue;
            }
            MirrorLetter.Content content = content(room, report, user);
            try {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("List-Unsubscribe", "<" + content.stopUrl() + ">");
                headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
                mailer.send(new SmtpMailer.Mail(to, "no-reply@beinema.ir", "بین‌ما", MirrorLetter.subject(content),
                        MirrorLetter.text(content), MirrorLetter.html(content), null, headers));
                sent++;
            } catch (RuntimeException failure) {
                log.warn("Mirror email of room {} to user {} failed: {}", room.getId(), user.getId(), failure.getMessage());
            }
        }
        return sent;
    }

    MirrorLetter.Content content(ChatRoom room, MirrorReport report, User user) {
        String[] mood = MOODS.getOrDefault(report.getMood(), MOODS.get("same"));
        return new MirrorLetter.Content(firstName(user), title(room), jalali(report.getDay()), report.getMood(),
                mood[0], mood[1], report.getDailySentence(), report.getWhereWeAre(), report.getWhereWeWere(),
                list(report.getNeedsJson()), list(report.getPatternsJson()), report.getNextStep(),
                siteUrl + "/chat-rooms/" + room.getId() + "?mirror=1",
                siteUrl + "/api/email/mirror-off?u=" + user.getId() + "&t=" + token(user.getId()));
    }

    // ------------------------------------------------------------------ for the app

    public record View(Long id, Long chatRoomId, String roomTitle, String day, String date, String mood,
                       String moodLabel, String dailySentence, String whereWeAre, String whereWeWere,
                       List<String> needs, List<String> patterns, String nextStep, int sessionsCount) {
    }

    /** The mirrors of one conversation, newest first, for a member of it. */
    public List<View> history(Long roomId, User user) {
        ChatRoom room = rooms.findById(roomId).orElseThrow(() -> new IllegalArgumentException("گفتگو پیدا نشد."));
        if (!participants.existsByChatRoomAndUser(room, user)) {
            throw new IllegalArgumentException("شما عضو این گفتگو نیستید.");
        }
        return reports.findTop30ByChatRoomIdAndStatusOrderByDayDesc(roomId, MirrorReport.DONE).stream()
                .map(report -> view(room, report))
                .toList();
    }

    /** The newest mirror of each of this person's conversations from the last week (the dashboard card). */
    public List<View> recent(User user) {
        Map<Long, ChatRoom> mine = new LinkedHashMap<>();
        for (ChatRoomParticipant person : participants.findAllForUser(user.getId())) {
            if (person.getMemberStatus() != ChatRoomMemberStatus.REMOVED) {
                mine.put(person.getChatRoom().getId(), person.getChatRoom());
            }
        }
        if (mine.isEmpty()) {
            return List.of();
        }
        LocalDate since = LocalDate.now(TEHRAN).minusDays(7);
        Set<Long> seen = new LinkedHashSet<>();
        List<View> out = new ArrayList<>();
        for (MirrorReport report : reports.findAllByChatRoomIdInAndStatusAndDayGreaterThanEqualOrderByDayDesc(
                mine.keySet(), MirrorReport.DONE, since)) {
            if (seen.add(report.getChatRoomId())) {
                out.add(view(mine.get(report.getChatRoomId()), report));
            }
        }
        return out;
    }

    private View view(ChatRoom room, MirrorReport report) {
        String[] mood = MOODS.getOrDefault(report.getMood(), MOODS.get("same"));
        return new View(report.getId(), report.getChatRoomId(), title(room), report.getDay().toString(),
                jalali(report.getDay()), report.getMood(), mood[0], report.getDailySentence(), report.getWhereWeAre(),
                report.getWhereWeWere(), list(report.getNeedsJson()), list(report.getPatternsJson()),
                report.getNextStep(), report.getSessionsCount());
    }

    // ------------------------------------------------------------------ turning the email off

    /** The link in the email: no more mirror emails for this person. */
    public boolean stopEmails(Long userId, String token) {
        if (userId == null || token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.US_ASCII), token(userId).getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        users.findById(userId).ifPresent(user -> {
            user.setMirrorEmailOff(true);
            users.save(user);
        });
        return true;
    }

    String token(Long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(String.valueOf(userId).getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    // ------------------------------------------------------------------ small things

    /** 2026-11-09 as «۱۸ آبان ۱۴۰۵». */
    static String jalali(LocalDate day) {
        String[] parts = MarketDataService.jalali(day).split("/");
        String text = Integer.parseInt(parts[2]) + " " + MONTHS[Integer.parseInt(parts[1]) - 1] + " " + parts[0];
        StringBuilder persian = new StringBuilder();
        for (char ch : text.toCharArray()) {
            persian.append(ch >= '0' && ch <= '9' ? (char) ('۰' + (ch - '0')) : ch);
        }
        return persian.toString();
    }

    private static String title(ChatRoom room) {
        return room.getTitle() == null || room.getTitle().isBlank() ? "گفتگوی شما" : room.getTitle().trim();
    }

    private static String firstName(User user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName().trim();
        }
        return user.hasRegisteredName() ? user.getName() : null;
    }

    private List<String> list(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception broken) {
            return List.of();
        }
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
