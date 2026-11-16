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
import com.coupleai.coupleai.beinema.insight.RelationshipInsightService;
import com.coupleai.coupleai.beinema.mail.AdminEmailService;
import com.coupleai.coupleai.beinema.mail.SmtpMailer;
import com.coupleai.coupleai.beinema.teach.LessonDigest;
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
 * The mirror of a conversation, one per day: written right after a session closes (and written again
 * after a later session that day). Nobody is interrupted with it: the dashboard card of the conversation
 * says it is ready and opens it, and whoever has not opened it within a day gets it by email, unless
 * they turned that off.
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

    /** Each person's own lesson cards ride in their own mirror email, or go on their own. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LessonDigest lessons;

    /** On a night without a mirror for them, the lessons go alone once the mirrors had time to be written. */
    static final java.time.Duration LESSONS_ALONE_AFTER = java.time.Duration.ofMinutes(45);

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

    /** A mirror not opened in the app within this time is emailed. */
    static final java.time.Duration EMAIL_AFTER = java.time.Duration.ofHours(24);
    /** Older than this, an unopened mirror is not emailed any more (the server was down, say). */
    static final java.time.Duration EMAIL_UNTIL = java.time.Duration.ofHours(72);
    /** How far back the dashboard looks for a mirror not opened yet. */
    static final java.time.Duration UNSEEN_FOR = java.time.Duration.ofDays(14);

    @org.springframework.beans.factory.annotation.Autowired
    private MirrorReadRepository reads;

    /**
     * Right after a session closed (its relationship path was just written): the mirror of that day is
     * written, or written again with this session in it. Nobody is told: the dashboard shows that it is
     * ready, and it is emailed only to whoever has not opened it within a day. Never throws.
     */
    public void afterSession(Long roomId, Instant endedAt) {
        try {
            writeFor(roomId, endedAt == null ? Instant.now() : endedAt);
        } catch (RuntimeException failure) {
            log.warn("Mirror of room {} after a session failed: {}", roomId, failure.getMessage());
        }
    }

    /**
     * Every quarter hour: a mirror whose writing after its session failed is tried again; unopened
     * mirrors are emailed a day after they were written; and at night the lessons of people who got no
     * mirror email go on their own.
     */
    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Tehran")
    public void nightly() {
        Instant now = Instant.now();
        Map<Long, Instant> lastEnd = new LinkedHashMap<>();
        for (SessionRecord session : sessions.findAllByEndedAtGreaterThanAndEndedAtLessThanEqualAndCompletedTrue(
                now.minus(java.time.Duration.ofHours(48)), now)) {
            lastEnd.merge(session.getChatRoomId(), session.getEndedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        lastEnd.forEach((roomId, endedAt) -> {
            try {
                writeFor(roomId, endedAt);
            } catch (RuntimeException failure) {
                log.warn("Mirror of room {} failed: {}", roomId, failure.getMessage());
            }
        });
        try {
            emailUnopened(now);
        } catch (RuntimeException failure) {
            log.warn("Mirror emails failed: {}", failure.getMessage());
        }
        Instant to = lastCutoff(LocalDateTime.now(TEHRAN)).atZone(TEHRAN).toInstant();
        if (!now.isBefore(to.plus(LESSONS_ALONE_AFTER))) {
            try {
                lessonsAlone(to);
            } catch (RuntimeException failure) {
                log.warn("Lesson emails before {} failed: {}", to, failure.getMessage());
            }
        }
    }

    /** The lessons of people who got no mirror tonight, in a short email of their own. */
    void lessonsAlone(Instant cutoff) {
        if (lessons == null || !mailer.configured()) {
            return;
        }
        lessons.due(cutoff).forEach((userId, due) -> {
            User user = users.findById(userId).orElse(null);
            String to = user == null ? null : AdminEmailService.address(user);
            if (to == null || Boolean.TRUE.equals(user.getMirrorEmailOff())) {
                return;
            }
            String stopUrl = siteUrl + "/api/email/mirror-off?u=" + user.getId() + "&t=" + token(user.getId());
            String name = firstName(user);
            String body = "<div style=\"font-size:15px;line-height:2;margin:0 0 14px;\">"
                    + (name == null ? "سلام،" : "سلام " + MirrorLetter.esc(name) + "،") + "</div>"
                    + LessonDigest.html(due, siteUrl + "/dashboard/lessons");
            String subject = LessonDigest.subject(due);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("List-Unsubscribe", "<" + stopUrl + ">");
            headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            try {
                mailer.send(new SmtpMailer.Mail(to, "no-reply@beinema.ir", "بین‌ما", subject,
                        (name == null ? "سلام،" : "سلام " + name + "،") + LessonDigest.text(due, siteUrl + "/dashboard/lessons")
                                + "\n—\nدیگر ایمیل شبانه نفرست: " + stopUrl,
                        MirrorLetter.frame(subject, "درس امروزت", body, stopUrl, "دیگر ایمیل شبانه نفرست"), null, headers));
                lessons.sent(due, Instant.now());
            } catch (RuntimeException failure) {
                log.warn("Lesson email to user {} failed: {}", userId, failure.getMessage());
            }
        });
    }

    /** The mirror of the day this session ended on, written once more if it was written before the session ended. */
    void writeFor(Long roomId, Instant endedAt) {
        LocalDate day = endedAt.atZone(TEHRAN).toLocalDate();
        MirrorReport report = reports.findByChatRoomIdAndDay(roomId, day).orElseGet(() -> {
            MirrorReport fresh = new MirrorReport();
            fresh.setChatRoomId(roomId);
            fresh.setDay(day);
            return fresh;
        });
        boolean coversThisSession = report.getBuiltAt() != null && !report.getBuiltAt().isBefore(endedAt);
        if (!MirrorReport.PENDING.equals(report.getStatus())) {
            if (coversThisSession || (report.getId() != null && report.getBuiltAt() == null
                    && report.getAttempts() >= MAX_ATTEMPTS)) {
                return;
            }
            // a later session that day: the mirror is written again with it
            report.setStatus(MirrorReport.PENDING);
            report.setAttempts(0);
        }
        if (report.getAttempts() >= MAX_ATTEMPTS) {
            return;
        }
        ChatRoom room = rooms.findById(roomId).orElse(null);
        if (room == null) {
            return;
        }
        Instant dayStart = day.atStartOfDay(TEHRAN).toInstant();
        int sessionsCount = (int) sessions.findAllByEndedAtGreaterThanAndEndedAtLessThanEqualAndCompletedTrue(
                        dayStart.minusNanos(1), dayStart.plus(java.time.Duration.ofDays(1)))
                .stream().filter(session -> roomId.equals(session.getChatRoomId())).count();
        report.setSessionsCount(Math.max(1, sessionsCount));
        report.setAttempts(report.getAttempts() + 1);
        RelationshipInsight insight;
        try {
            insight = insights.freshFor(roomId, endedAt);
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
        reports.save(report);
        log.info("Mirror of room {} for {} written", roomId, day);
    }

    /** Someone to email one mirror to, read inside a transaction (the members load lazily). */
    private record Outgoing(Long userId, String to, MirrorLetter.Content content) {
    }

    /** Each mirror written a day ago or more, to every member who has neither opened it nor had it emailed. */
    void emailUnopened(Instant now) {
        if (!mailer.configured()) {
            return;
        }
        for (MirrorReport report : reports.findAllByStatusAndBuiltAtBetween(MirrorReport.DONE,
                now.minus(EMAIL_UNTIL), now.minus(EMAIL_AFTER))) {
            List<Outgoing> outgoing = readOnly.execute(status -> recipients(report));
            int sent = 0;
            for (Outgoing person : outgoing == null ? List.<Outgoing>of() : outgoing) {
                MirrorRead read = reads.findByReportIdAndUserId(report.getId(), person.userId()).orElseGet(() -> {
                    MirrorRead fresh = new MirrorRead();
                    fresh.setReportId(report.getId());
                    fresh.setUserId(person.userId());
                    return fresh;
                });
                if (read.handled(report.getBuiltAt())) {
                    continue;
                }
                if (send(report, person, now)) {
                    read.setEmailedAt(Instant.now());
                    reads.save(read);
                    sent++;
                }
            }
            if (sent > 0) {
                report.setEmailed(report.getEmailed() + sent);
                reports.save(report);
                log.info("Mirror {} of room {} emailed to {} who had not opened it", report.getId(),
                        report.getChatRoomId(), sent);
            }
        }
    }

    /** The active members with an email address who did not turn the mirror email off. */
    private List<Outgoing> recipients(MirrorReport report) {
        ChatRoom room = rooms.findById(report.getChatRoomId()).orElse(null);
        if (room == null) {
            return List.of();
        }
        List<Outgoing> out = new ArrayList<>();
        for (ChatRoomParticipant person : participants.findAllByChatRoom(room)) {
            User user = person.getUser();
            if (user == null || person.getMemberStatus() == ChatRoomMemberStatus.LEFT
                    || person.getMemberStatus() == ChatRoomMemberStatus.REMOVED
                    || Boolean.TRUE.equals(user.getMirrorEmailOff())) {
                continue;
            }
            String to = AdminEmailService.address(user);
            if (to != null) {
                out.add(new Outgoing(user.getId(), to, content(room, report, user)));
            }
        }
        return out;
    }

    private boolean send(MirrorReport report, Outgoing person, Instant now) {
        MirrorLetter.Content content = person.content();
        // this person's own lesson cards, in their email only
        List<LessonDigest.Due> due = lessons == null ? List.of() : lessons.dueFor(person.userId(), now);
        String lessonsUrl = siteUrl + "/dashboard/lessons";
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("List-Unsubscribe", "<" + content.stopUrl() + ">");
            headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            mailer.send(new SmtpMailer.Mail(person.to(), "no-reply@beinema.ir", "بین‌ما", MirrorLetter.subject(content),
                    MirrorLetter.text(content, due.isEmpty() ? "" : LessonDigest.text(due, lessonsUrl)),
                    MirrorLetter.html(content, due.isEmpty() ? "" : LessonDigest.html(due, lessonsUrl)), null, headers));
            if (!due.isEmpty()) {
                lessons.sent(due, Instant.now());
            }
            return true;
        } catch (RuntimeException failure) {
            log.warn("Mirror email of room {} to user {} failed: {}", report.getChatRoomId(), person.userId(),
                    failure.getMessage());
            return false;
        }
    }

    MirrorLetter.Content content(ChatRoom room, MirrorReport report, User user) {
        String[] mood = MOODS.getOrDefault(report.getMood(), MOODS.get("same"));
        return new MirrorLetter.Content(firstName(user), title(room), jalali(report.getDay()), report.getMood(),
                mood[0], mood[1], report.getDailySentence(), report.getWhereWeAre(), report.getWhereWeWere(),
                list(report.getNeedsJson()), list(report.getPatternsJson()), report.getNextStep(),
                siteUrl + "/dashboard?mirror=" + report.getId(),
                siteUrl + "/api/email/mirror-off?u=" + user.getId() + "&t=" + token(user.getId()));
    }

    /**
     * A sample mirror to this person's own address, through the real mail server and with real
     * links: their newest conversation (with its mirror panel open) and their own «stop» link.
     * The words are the newest mirror of one of their conversations, or a made-up sample when
     * they have none. Returns the address it went to.
     */
    public String sendSample(User user) {
        String to = AdminEmailService.address(user);
        if (to == null) {
            throw new IllegalArgumentException("برای حساب تو ایمیلی ثبت نشده است.");
        }
        if (!mailer.configured()) {
            throw new IllegalArgumentException("ارسال ایمیل روی این سرور تنظیم نشده است.");
        }
        List<View> mine = recent(user);
        ChatRoomParticipant newest = participants.findAllForUser(user.getId()).stream().findFirst().orElse(null);
        String roomUrl = mine.isEmpty()
                ? newest == null ? siteUrl + "/dashboard" : siteUrl + "/chat-rooms/" + newest.getChatRoom().getId() + "?mirror=1"
                : siteUrl + "/chat-rooms/" + mine.get(0).chatRoomId() + "?mirror=1";
        String stopUrl = siteUrl + "/api/email/mirror-off?u=" + user.getId() + "&t=" + token(user.getId());
        MirrorLetter.Content content;
        if (!mine.isEmpty()) {
            View view = mine.get(0);
            String[] mood = MOODS.getOrDefault(view.mood(), MOODS.get("same"));
            content = new MirrorLetter.Content(firstName(user), view.roomTitle(), view.date(), view.mood(), mood[0], mood[1],
                    view.dailySentence(), view.whereWeAre(), view.whereWeWere(), view.needs(), view.patterns(),
                    view.nextStep(), roomUrl, stopUrl);
        } else {
            String[] mood = MOODS.get("softer");
            content = new MirrorLetter.Content(firstName(user), "گفتگوی نمونه", jalali(LocalDate.now(TEHRAN)), "softer",
                    mood[0], mood[1], "وقتی هر دو برای خرج‌های خانه برنامه داریم، بحث کمتر به دعوا می‌رسد.",
                    "امروز دربارهٔ خرج‌های ماه حرف زدیم و به یک بودجهٔ ساده نزدیک شدیم.", null,
                    List.of("شفافیت در خرج‌ها", "تصمیم مشترک"), List.of(),
                    "تا جمعه هر کدام خرج‌های ثابت ماه را بنویسیم و شنبه ده دقیقه با هم مرورش کنیم.", roomUrl, stopUrl);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe", "<" + stopUrl + ">");
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        // their newest lesson card, so the sample shows that block too (nothing is marked as sent)
        List<LessonDigest.Due> myLessons = lessons == null ? List.of() : lessons.newestFor(user.getId());
        String lessonsUrl = siteUrl + "/dashboard/lessons";
        try {
            mailer.send(new SmtpMailer.Mail(to, "no-reply@beinema.ir", "بین‌ما", MirrorLetter.subject(content) + " (نمونه)",
                    MirrorLetter.text(content, myLessons.isEmpty() ? "" : LessonDigest.text(myLessons, lessonsUrl)),
                    MirrorLetter.html(content, myLessons.isEmpty() ? "" : LessonDigest.html(myLessons, lessonsUrl)), null, headers));
        } catch (IllegalStateException failure) {
            throw new IllegalArgumentException("سرور ایمیل نمونه را نپذیرفت. کمی بعد دوباره امتحان کن.");
        }
        return to;
    }

    // ------------------------------------------------------------------ for the app

    public record View(Long id, Long chatRoomId, String roomTitle, String day, String date, String mood,
                       String moodLabel, String dailySentence, String whereWeAre, String whereWeWere,
                       List<String> needs, List<String> patterns, String nextStep, int sessionsCount) {
    }

    /**
     * For the dashboard: the newest mirror of each of this person's conversations that they have not
     * opened since it was (last) written. The card of that conversation says it is ready.
     */
    public List<View> unopened(User user) {
        Map<Long, ChatRoom> mine = new LinkedHashMap<>();
        for (ChatRoomParticipant person : participants.findAllForUser(user.getId())) {
            if (person.getMemberStatus() != ChatRoomMemberStatus.REMOVED
                    && person.getMemberStatus() != ChatRoomMemberStatus.LEFT) {
                mine.put(person.getChatRoom().getId(), person.getChatRoom());
            }
        }
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, MirrorReport> newest = new LinkedHashMap<>();
        for (MirrorReport report : reports.findAllByChatRoomIdInAndStatusAndBuiltAtAfterOrderByBuiltAtDesc(
                mine.keySet(), MirrorReport.DONE, Instant.now().minus(UNSEEN_FOR))) {
            newest.putIfAbsent(report.getChatRoomId(), report);
        }
        if (newest.isEmpty()) {
            return List.of();
        }
        Map<Long, MirrorRead> myReads = new LinkedHashMap<>();
        for (MirrorRead read : reads.findAllByReportIdIn(newest.values().stream().map(MirrorReport::getId).toList())) {
            if (user.getId().equals(read.getUserId())) {
                myReads.put(read.getReportId(), read);
            }
        }
        List<View> out = new ArrayList<>();
        for (MirrorReport report : newest.values()) {
            MirrorRead read = myReads.get(report.getId());
            if (read == null || !read.seen(report.getBuiltAt())) {
                out.add(view(mine.get(report.getChatRoomId()), report));
            }
        }
        return out;
    }

    /** One mirror, for a member of its conversation (the link in the email opens it). */
    public View one(Long reportId, User user) {
        MirrorReport report = reports.findById(reportId)
                .filter(item -> MirrorReport.DONE.equals(item.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("آینه پیدا نشد."));
        ChatRoom room = rooms.findById(report.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("گفتگو پیدا نشد."));
        if (!participants.existsByChatRoomAndUser(room, user)) {
            throw new IllegalArgumentException("شما عضو این گفتگو نیستید.");
        }
        return view(room, report);
    }

    /** This person opened this mirror: it is not shown as ready to them, and not emailed to them. */
    public void opened(Long reportId, User user) {
        one(reportId, user);
        MirrorRead read = reads.findByReportIdAndUserId(reportId, user.getId()).orElseGet(() -> {
            MirrorRead fresh = new MirrorRead();
            fresh.setReportId(reportId);
            fresh.setUserId(user.getId());
            return fresh;
        });
        read.setSeenAt(Instant.now());
        reads.save(read);
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
