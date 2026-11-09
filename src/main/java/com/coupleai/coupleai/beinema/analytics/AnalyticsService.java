package com.coupleai.coupleai.beinema.analytics;

import com.coupleai.coupleai.beinema.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The site's own visit log, first party and without personal data: a visit (session) per 30
 * minutes of activity, every page opened in it, and who is on the site right now. The browser
 * sends a page view on each route and a heartbeat every 30 seconds while the tab is in view.
 * <p>
 * Nothing that identifies a person is kept: no IP address (only used for flood control in
 * memory), no full user agent (only device, browser and system), referrers only by host.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    /** Seen within this long: on the site now. */
    static final Duration ONLINE = Duration.ofSeconds(90);
    /** Idle this long: the visit is over (the browser starts a new one too). */
    static final Duration VISIT_GAP = Duration.ofMinutes(30);
    private static final int PER_MINUTE = 120;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9-]{8,40}");

    private final VisitSessionRepository sessions;
    private final PageViewRepository views;
    private final JdbcTemplate jdbc;

    /** The visits of the last 30 minutes, by the browser's session id. */
    private final Map<String, Active> active = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> flood = new ConcurrentHashMap<>();
    private final Object[] locks = new Object[64];

    {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    /** A visit being followed in memory. */
    static final class Active {
        final long id;
        final String visitorId;
        final LocalDateTime startedAt;
        final String device;
        final String source;
        final boolean member;
        volatile LocalDateTime lastSeen;
        volatile String path;
        volatile boolean dirty;

        Active(long id, String visitorId, LocalDateTime startedAt, String device, String source, boolean member, String path) {
            this.id = id;
            this.visitorId = visitorId;
            this.startedAt = startedAt;
            this.device = device;
            this.source = source;
            this.member = member;
            this.lastSeen = startedAt;
            this.path = path;
        }
    }

    /** What the browser sends with a page view. */
    public record Hit(String vid, String sid, String path, String referrer,
                      String utmSource, String utmMedium, String utmCampaign) {
    }

    static LocalDateTime now() {
        return LocalDateTime.now(TEHRAN).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Too many events from one address this minute: ignored, the site never notices. */
    boolean flooded(String address) {
        return address != null && flood.computeIfAbsent(address, a -> new AtomicInteger()).incrementAndGet() > PER_MINUTE;
    }

    /** A page was opened. Returns false when it was not counted (robot, broken ids, flood). */
    @Transactional
    public boolean view(Hit hit, String userAgent, String address, Long userId) {
        if (hit == null || Traffic.robot(userAgent) || flooded(address)
                || hit.sid() == null || !ID.matcher(hit.sid()).matches()) {
            return false;
        }
        String visitor = hit.vid() != null && ID.matcher(hit.vid()).matches() ? hit.vid() : "s-" + hit.sid();
        String path = Traffic.path(hit.path());
        LocalDateTime at = now();
        Active visit;
        synchronized (locks[Math.floorMod(hit.sid().hashCode(), locks.length)]) {
            visit = active.get(hit.sid());
            if (visit == null) {
                visit = sessions.findBySid(hit.sid())
                        .map(found -> new Active(found.getId(), found.getVisitorId(), found.getStartedAt(), found.getDevice(),
                                found.getSource(), found.getUserId() != null, path))
                        .orElseGet(() -> start(hit, visitor, path, at, userAgent, userId));
                active.put(hit.sid(), visit);
            }
        }
        visit.lastSeen = at;
        visit.path = path;
        visit.dirty = false;
        sessions.viewed(visit.id, at, path);
        PageView view = new PageView();
        view.setSessionId(visit.id);
        view.setVisitorId(visit.visitorId);
        view.setPath(path);
        view.setViewedAt(at);
        views.save(view);
        return true;
    }

    private Active start(Hit hit, String visitor, String path, LocalDateTime at, String userAgent, Long userId) {
        Traffic.Source source = Traffic.source(hit.referrer(), hit.utmSource(), hit.utmMedium());
        VisitSession session = new VisitSession();
        session.setSid(hit.sid());
        session.setVisitorId(visitor);
        session.setStartedAt(at);
        session.setLastSeenAt(at);
        session.setPageviews(0);
        session.setEntryPath(path);
        session.setExitPath(path);
        session.setSource(source.key());
        session.setReferrerHost(Traffic.clean(source.host(), 120));
        session.setUtmSource(Traffic.clean(hit.utmSource(), 80));
        session.setUtmMedium(Traffic.clean(hit.utmMedium(), 80));
        session.setUtmCampaign(Traffic.clean(hit.utmCampaign(), 80));
        session.setDevice(Traffic.device(userAgent));
        session.setBrowser(Traffic.browser(userAgent));
        session.setSystem(Traffic.system(userAgent));
        session.setUserId(userId);
        session.setNewVisitor(!sessions.existsByVisitorId(visitor));
        session = sessions.save(session);
        return new Active(session.getId(), visitor, at, session.getDevice(), session.getSource(), userId != null, path);
    }

    /** The tab is still open and in view. Only memory; the database hears it once a minute. */
    public void ping(String sid, String path, String userAgent, String address) {
        if (sid == null || Traffic.robot(userAgent) || flooded(address)) {
            return;
        }
        Active visit = active.get(sid);
        if (visit != null) {
            visit.lastSeen = now();
            if (path != null) {
                visit.path = Traffic.path(path);
            }
            visit.dirty = true;
        }
    }

    /** Writes the heartbeats, forgets finished visits and resets the flood counters. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void flush() {
        flood.clear();
        LocalDateTime over = now().minus(VISIT_GAP);
        for (Map.Entry<String, Active> entry : active.entrySet()) {
            Active visit = entry.getValue();
            if (visit.dirty) {
                visit.dirty = false;
                sessions.seen(visit.id, visit.lastSeen);
            }
            if (visit.lastSeen.isBefore(over)) {
                active.remove(entry.getKey());
            }
        }
    }

    // ---------------------------------------------------------------- the report

    public record Online(String path, String device, String source, long seconds, boolean member) {
    }

    public record Recent(LocalDateTime startedAt, String source, String referrerHost, String entryPath, String exitPath,
                         int pageviews, long seconds, String device, String browser, String system,
                         boolean member, boolean newVisitor, boolean online) {
    }

    /** Who is on the site right now, and the latest visits. */
    public Map<String, Object> live() {
        LocalDateTime now = now();
        LocalDateTime cutoff = now.minus(ONLINE);
        List<Online> online = active.values().stream()
                .filter(visit -> !visit.lastSeen.isBefore(cutoff))
                .sorted(Comparator.comparing((Active visit) -> visit.startedAt).reversed())
                .map(visit -> new Online(visit.path, visit.device, visit.source,
                        Duration.between(visit.startedAt, now).getSeconds(), visit.member))
                .toList();
        Map<String, Long> pages = new LinkedHashMap<>();
        online.stream().map(Online::path).forEach(path -> pages.merge(path, 1L, Long::sum));
        List<Recent> recent = sessions.findTop20ByOrderByStartedAtDesc().stream()
                .map(s -> {
                    Active visit = active.get(s.getSid());
                    boolean here = visit != null && !visit.lastSeen.isBefore(cutoff);
                    LocalDateTime last = visit != null && visit.lastSeen.isAfter(s.getLastSeenAt()) ? visit.lastSeen : s.getLastSeenAt();
                    return new Recent(s.getStartedAt(), s.getSource(), s.getReferrerHost(), s.getEntryPath(),
                            visit != null ? visit.path : s.getExitPath(), s.getPageviews(),
                            Math.max(0, Duration.between(s.getStartedAt(), last).getSeconds()),
                            s.getDevice(), s.getBrowser(), s.getSystem(), s.getUserId() != null, s.isNewVisitor(), here);
                })
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("online", online.size());
        out.put("members", online.stream().filter(Online::member).count());
        out.put("pages", pages.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> Map.of("path", e.getKey(), "count", e.getValue())).toList());
        out.put("visitors", online);
        out.put("recent", recent);
        out.put("at", now);
        return out;
    }

    /** A period of the report and the one before it, of the same length. */
    record Period(String key, LocalDateTime from, LocalDateTime to, LocalDateTime previousFrom, LocalDateTime previousTo,
                  boolean hourly) {
    }

    static Period period(String key, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDateTime midnight = today.atStartOfDay();
        return switch (key == null ? "7d" : key) {
            case "today" -> new Period("today", midnight, now, midnight.minusDays(1), now.minusDays(1), true);
            case "yesterday" -> new Period("yesterday", midnight.minusDays(1), midnight,
                    midnight.minusDays(2), midnight.minusDays(1), true);
            case "week" -> {
                LocalDateTime from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).atStartOfDay();
                yield new Period("week", from, now, from.minusDays(7), now.minusDays(7), false);
            }
            case "month" -> {
                LocalDateTime from = jalaliMonthStart(today).atStartOfDay();
                LocalDateTime previous = jalaliMonthStart(from.toLocalDate().minusDays(1)).atStartOfDay();
                yield new Period("month", from, now, previous, previous.plus(Duration.between(from, now)), false);
            }
            case "30d" -> days("30d", 30, today, now);
            case "90d" -> days("90d", 90, today, now);
            default -> days("7d", 7, today, now);
        };
    }

    private static Period days(String key, int count, LocalDate today, LocalDateTime now) {
        LocalDateTime from = today.minusDays(count - 1L).atStartOfDay();
        return new Period(key, from, now, from.minusDays(count), now.minusDays(count), false);
    }

    static LocalDate jalaliMonthStart(LocalDate day) {
        String[] parts = MarketDataService.jalali(day).split("/");
        return day.minusDays(Integer.parseInt(parts[2]) - 1L);
    }

    private static final String TOTALS = """
            select count(*) sessions,
                   count(distinct visitor_id) visitors,
                   coalesce(sum(new_visitor), 0) new_visitors,
                   coalesce(sum(user_id is not null), 0) members,
                   coalesce(sum(pageviews <= 1 and timestampdiff(second, started_at, last_seen_at) < 10), 0) bounces,
                   coalesce(avg(timestampdiff(second, started_at, last_seen_at)), 0) seconds
            from visit_sessions where started_at >= ? and started_at < ?""";

    private Map<String, Object> totals(LocalDateTime from, LocalDateTime to) {
        Map<String, Object> row = new LinkedHashMap<>(jdbc.queryForMap(TOTALS, from, to));
        Long count = jdbc.queryForObject("select count(*) from page_views where viewed_at >= ? and viewed_at < ?", Long.class, from, to);
        row.put("pageviews", count == null ? 0 : count);
        return row;
    }

    private List<Map<String, Object>> group(String column, LocalDateTime from, LocalDateTime to, int limit) {
        return jdbc.queryForList("select " + column + " name, count(*) sessions, count(distinct visitor_id) visitors, "
                + "coalesce(sum(pageviews <= 1 and timestampdiff(second, started_at, last_seen_at) < 10), 0) bounces "
                + "from visit_sessions where started_at >= ? and started_at < ? and " + column + " is not null "
                + "group by " + column + " order by sessions desc limit " + limit, from, to);
    }

    /** The whole report for one period. */
    public Map<String, Object> report(String key) {
        LocalDateTime now = now();
        Period p = period(key, now);
        LocalDateTime to = p.to().plusSeconds(1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("range", p.key());
        out.put("from", p.from());
        out.put("to", p.to());
        out.put("hourly", p.hourly());
        out.put("totals", totals(p.from(), to));
        out.put("previous", totals(p.previousFrom(), p.previousTo().plusSeconds(1)));
        out.put("series", series(p, to));
        out.put("pages", jdbc.queryForList("select path name, count(*) views, count(distinct visitor_id) visitors "
                + "from page_views where viewed_at >= ? and viewed_at < ? group by path order by views desc limit 15", p.from(), to));
        out.put("entries", group("entry_path", p.from(), to, 10));
        out.put("exits", group("exit_path", p.from(), to, 10));
        out.put("sources", group("source", p.from(), to, 20));
        out.put("referrers", group("referrer_host", p.from(), to, 15));
        out.put("campaigns", jdbc.queryForList("select coalesce(utm_campaign, '—') campaign, coalesce(utm_source, '—') source, "
                + "coalesce(utm_medium, '—') medium, count(*) sessions, count(distinct visitor_id) visitors from visit_sessions "
                + "where started_at >= ? and started_at < ? and (utm_campaign is not null or utm_source is not null) "
                + "group by utm_campaign, utm_source, utm_medium order by sessions desc limit 12", p.from(), to));
        out.put("devices", group("device", p.from(), to, 5));
        out.put("browsers", group("browser", p.from(), to, 8));
        out.put("systems", group("os", p.from(), to, 8));
        out.put("heatmap", jdbc.queryForList("select dayofweek(started_at) day, hour(started_at) hour, count(*) sessions "
                + "from visit_sessions where started_at >= ? group by day, hour", now.toLocalDate().minusDays(27).atStartOfDay()));
        out.put("glance", glance(now));
        return out;
    }

    /** The day, hour by hour, or the period, day by day, with empty buckets filled in. */
    private List<Map<String, Object>> series(Period p, LocalDateTime to) {
        String format = p.hourly() ? "%Y-%m-%d %H" : "%Y-%m-%d";
        Map<String, long[]> buckets = new LinkedHashMap<>();
        DateTimeFormatter label = DateTimeFormatter.ofPattern(p.hourly() ? "yyyy-MM-dd HH" : "yyyy-MM-dd");
        LocalDateTime cursor = p.hourly() ? p.from() : p.from().toLocalDate().atStartOfDay();
        while (cursor.isBefore(to)) {
            buckets.put(cursor.format(label), new long[3]);
            cursor = p.hourly() ? cursor.plusHours(1) : cursor.plusDays(1);
        }
        jdbc.query("select date_format(started_at, '" + format + "') b, count(distinct visitor_id) v, count(*) s "
                + "from visit_sessions where started_at >= ? and started_at < ? group by b", rs -> {
            long[] bucket = buckets.get(rs.getString("b"));
            if (bucket != null) {
                bucket[0] = rs.getLong("v");
                bucket[2] = rs.getLong("s");
            }
        }, p.from(), to);
        jdbc.query("select date_format(viewed_at, '" + format + "') b, count(*) n "
                + "from page_views where viewed_at >= ? and viewed_at < ? group by b", rs -> {
            long[] bucket = buckets.get(rs.getString("b"));
            if (bucket != null) {
                bucket[1] = rs.getLong("n");
            }
        }, p.from(), to);
        List<Map<String, Object>> out = new ArrayList<>();
        buckets.forEach((at, n) -> out.add(Map.of("at", at, "visitors", n[0], "views", n[1], "sessions", n[2])));
        return out;
    }

    /** Visitors of today, this week and this month at a glance, whatever period is open. */
    private Map<String, Object> glance(LocalDateTime now) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("today", "week", "month")) {
            Period p = period(key, now);
            out.put(key, jdbc.queryForMap("select count(distinct visitor_id) visitors, count(*) sessions "
                    + "from visit_sessions where started_at >= ? and started_at < ?", p.from(), p.to().plusSeconds(1)));
        }
        Long views = jdbc.queryForObject("select count(*) from page_views where viewed_at >= ?", Long.class,
                now.toLocalDate().atStartOfDay());
        out.put("viewsToday", views == null ? 0 : views);
        return out;
    }
}
