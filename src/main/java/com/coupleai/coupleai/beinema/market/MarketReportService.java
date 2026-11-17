package com.coupleai.coupleai.beinema.market;

import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pouria's market report: today's prices, a year of each market read like an analyst would
 * (MarketAnalysis), what 100 tomans became in each, the official inflation and the reference rates
 * (deposits, housing) with their sources, and a few plain sentences of what all this shows. It
 * describes the past and the present; it never forecasts and never says what to buy.
 *
 * Every report is kept: made in a conversation it belongs to that conversation's members, made from
 * the dashboard it belongs to the person who made it. Both are listed in «گزارش‌های مالی».
 */
@Service
@RequiredArgsConstructor
public class MarketReportService {

    static final String SCHEMA = "market-report.v1";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** A report asked for again from the dashboard within this time is the same one. */
    static final Duration REUSE = Duration.ofHours(1);
    /** Points of each market's line in a report. */
    static final int POINTS = 120;
    /** The markets of the «100 tomans a year ago» chart, in this order. */
    static final List<String> GROWTH = List.of("دلار آزاد", "سکهٔ امامی", "طلای ۱۸ عیار، هر گرم", MarketDataService.INDEX_LABEL);

    private final MarketDataService market;
    private final FinanceReferenceService reference;
    private final MarketReportRepository reports;
    private final ChatRoomParticipantRepository participants;

    // ------------------------------------------------------------------ making one

    /** A report from the prices now; null when the prices are missing or old. */
    @Transactional
    public MarketReport create(Long roomId, Long userId) {
        MarketDataService.Snapshot now = market.snapshot();
        if (now.quotes().isEmpty() || now.stale(Instant.now())) {
            return null;
        }
        MarketReport report = new MarketReport();
        report.setPublicId(UUID.randomUUID().toString());
        report.setChatRoomId(roomId);
        report.setUserId(userId);
        try {
            report.setDataJson(JSON.writeValueAsString(build(now, reference.current(), LocalDate.now(MarketDataService.TEHRAN))));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
        return reports.save(report);
    }

    /** The whole report as the browser shows it. */
    static Map<String, Object> build(MarketDataService.Snapshot now, JsonNode reference, LocalDate today) {
        Double inflation = now.inflation() == null || now.inflation().consumer() == null ? null : now.inflation().consumer().percent();
        Double ounce = price(now.quotes(), "انس جهانی طلا");
        Double dollar = price(now.quotes(), "دلار آزاد");
        Map<String, List<double[]>> history = now.history() == null ? Map.of() : now.history();

        List<Map<String, Object>> assets = new ArrayList<>();
        List<MarketAnalysis.Analysis> analyses = new ArrayList<>();
        for (MarketDataService.Quote quote : now.quotes()) {
            List<double[]> series = history.getOrDefault(quote.label(), List.of());
            MarketAnalysis.Analysis analysis = MarketAnalysis.of(quote.label(), quote.unit(), quote.price(), series, today,
                    "دلار".equals(quote.unit()) ? null : inflation);
            analyses.add(analysis);
            Map<String, Object> asset = new LinkedHashMap<>();
            asset.put("label", quote.label());
            asset.put("unit", quote.unit());
            asset.put("price", quote.price());
            asset.put("day", quote.day());
            asset.put("changes", analysis.changes());
            asset.put("volatility", analysis.volatility());
            asset.put("maxDrawdown", analysis.maxDrawdown());
            asset.put("fromHigh", analysis.fromHigh());
            asset.put("rangePlace", analysis.rangePlace());
            asset.put("yearHigh", analysis.yearHigh());
            asset.put("yearLow", analysis.yearLow());
            asset.put("trend", analysis.trend());
            asset.put("realYear", analysis.realYear());
            asset.put("premium", MarketAnalysis.premium(quote.label(), quote.price(), ounce, dollar));
            asset.put("series", MarketAnalysis.thin(MarketAnalysis.withToday(series, today, quote.price()), today, POINTS));
            assets.add(asset);
        }

        Double depositYear = null;
        for (JsonNode rate : reference.path("deposit").path("rates")) {
            if (rate.path("months").asInt(-1) == 12) {
                depositYear = rate.path("percent").asDouble();
            }
        }
        Map<String, List<double[]>> growthSeries = new LinkedHashMap<>();
        for (String label : GROWTH) {
            List<double[]> series = history.get(label);
            Double price = price(now.quotes(), label);
            if (series != null && price != null) {
                growthSeries.put(label, MarketAnalysis.withToday(series, today, price));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", SCHEMA);
        out.put("updatedAt", now.updatedAt().toEpochMilli());
        out.put("day", MarketDataService.jalali(today));
        out.put("source", "tgju.org، tsetmc.com");
        out.put("assets", assets);
        out.put("growth", MarketAnalysis.growthOf100(growthSeries, today, depositYear, inflation));
        out.put("inflation", now.inflation());
        Map<String, Object> deposit = JSON.convertValue(reference.path("deposit"), Map.class);
        deposit.put("realYear", depositYear == null ? null : MarketAnalysis.realRate(depositYear, inflation));
        out.put("deposit", deposit);
        out.put("housing", JSON.convertValue(reference.path("housing"), Map.class));
        out.put("insights", insights(analyses, assets, inflation, depositYear, now.inflation()));
        return out;
    }

    private static Double price(List<MarketDataService.Quote> quotes, String label) {
        return quotes.stream().filter(quote -> quote.label().equals(label)).map(MarketDataService.Quote::price).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ the plain sentences

    /** What the figures show, in a few Persian sentences; descriptive, never a forecast or a recommendation. */
    static List<String> insights(List<MarketAnalysis.Analysis> analyses, List<Map<String, Object>> assets, Double inflation,
                                 Double depositYear, OfficialInflation.Rates rates) {
        List<String> out = new ArrayList<>();
        // the toman markets (and the index), so a dollar return is never ranked against a toman one
        List<MarketAnalysis.Analysis> local = analyses.stream().filter(a -> !"دلار".equals(a.unit())).toList();
        List<MarketAnalysis.Analysis> withYear = local.stream().filter(a -> a.changes().get("year") != null)
                .sorted(Comparator.comparing((MarketAnalysis.Analysis a) -> a.changes().get("year")).reversed()).toList();
        if (withYear.size() >= 2) {
            MarketAnalysis.Analysis best = withYear.get(0);
            MarketAnalysis.Analysis worst = withYear.get(withYear.size() - 1);
            out.add("در یک سال گذشته، از بازارهای ریالی بیشترین تغییر قیمت مال «" + best.label() + "» بوده (" + signed(best.changes().get("year"))
                    + ") و کمترین مال «" + worst.label() + "» (" + signed(worst.changes().get("year")) + ").");
        }
        if (inflation != null) {
            String period = rates != null && rates.consumer() != null ? " (" + digits(rates.consumer().period()) + ")" : "";
            StringBuilder line = new StringBuilder("تورم رسمی مصرف‌کننده" + period + " " + plain(inflation) + " است.");
            if (depositYear != null) {
                Double real = MarketAnalysis.realRate(depositYear, inflation);
                line.append(" با این تورم، سپردهٔ یک‌ساله با سقف رسمی ").append(plain(depositYear))
                        .append(" بازده واقعی حدود ").append(signed(real)).append(" دارد؛ ")
                        .append(real < 0 ? "یعنی قدرت خرید پولی که در سپرده می‌ماند کم می‌شود." : "یعنی از تورم جلوتر است.");
            }
            out.add(line.toString());
            List<String> ahead = withYear.stream().filter(a -> a.realYear() != null && a.realYear() > 0).map(MarketAnalysis.Analysis::label).toList();
            List<String> behind = withYear.stream().filter(a -> a.realYear() != null && a.realYear() <= 0).map(MarketAnalysis.Analysis::label).toList();
            if (!ahead.isEmpty() || !behind.isEmpty()) {
                out.add("در یک سال گذشته " + (ahead.isEmpty() ? "هیچ‌کدام از این بازارها از تورم جلو نزده" : join(ahead) + " از تورم جلو زده‌اند")
                        + (behind.isEmpty() ? "." : " و " + join(behind) + " عقب مانده‌اند."));
            }
        }
        List<MarketAnalysis.Analysis> swings = analyses.stream().filter(a -> a.volatility() != null)
                .sorted(Comparator.comparing(MarketAnalysis.Analysis::volatility).reversed()).toList();
        if (swings.size() >= 2) {
            MarketAnalysis.Analysis most = swings.get(0);
            MarketAnalysis.Analysis least = swings.get(swings.size() - 1);
            out.add("پرنوسان‌ترین بازار در سه ماه اخیر «" + most.label() + "» بوده، با نوسان سالانهٔ حدود " + plain(most.volatility())
                    + "؛ و کم‌نوسان‌ترین «" + least.label() + "» (حدود " + plain(least.volatility())
                    + "). نوسان بیشتر یعنی بالا و پایین تندتر، در هر دو جهت.");
        }
        analyses.stream().filter(a -> a.maxDrawdown() != null).min(Comparator.comparing(MarketAnalysis.Analysis::maxDrawdown))
                .filter(a -> a.maxDrawdown() < -10)
                .ifPresent(a -> out.add("«" + a.label() + "» در یک سال گذشته تا " + plain(a.maxDrawdown())
                        + " از سقفش پایین آمده" + (a.fromHigh() != null && a.fromHigh() < -1
                        ? " و الان " + plain(a.fromHigh()) + " زیر سقف یک‌ساله‌اش است." : " و الان نزدیک سقف یک‌ساله‌اش است.")));
        assets.stream().filter(asset -> "سکهٔ امامی".equals(asset.get("label")) && asset.get("premium") != null).findFirst()
                .ifPresent(asset -> {
                    double premium = (Double) asset.get("premium");
                    out.add(premium >= 0
                            ? "حباب تقریبی سکهٔ امامی " + plain(premium) + " است؛ یعنی سکه این‌قدر بالاتر از ارزش طلای خودش (به قیمت انس جهانی و دلار آزاد) معامله می‌شود. حباب می‌تواند کم یا زیاد شود و بخشی از ریسک خرید سکه است."
                            : "سکهٔ امامی حدود " + plain(premium) + " پایین‌تر از ارزش طلای خودش (به قیمت انس جهانی و دلار آزاد) معامله می‌شود.");
                });
        List<String> up = local.stream().filter(a -> "UP".equals(a.trend())).map(MarketAnalysis.Analysis::label).toList();
        List<String> down = local.stream().filter(a -> "DOWN".equals(a.trend())).map(MarketAnalysis.Analysis::label).toList();
        if (!up.isEmpty() || !down.isEmpty()) {
            out.add((up.isEmpty() ? "" : "قیمت " + join(up) + " بالای میانگین ۵۰ و ۲۰۰ روزه‌اش است (روند چند ماه اخیر صعودی بوده)"
                    + (down.isEmpty() ? "." : "؛ ")) + (down.isEmpty() ? "" : "قیمت " + join(down) + " زیر هر دو میانگین است (روند نزولی).")
                    + " روند گذشته آینده را تضمین نمی‌کند.");
        }
        return out;
    }

    /** A size in Persian, without its sign: ۳۱٫۸٪ */
    static String plain(Double value) {
        return value == null ? "نامعلوم" : digits(String.format(Locale.ROOT, "%.1f", Math.abs(value))).replace('.', '٫') + "٪";
    }

    /** A change in Persian words, so its sign never jumps around in right-to-left text: «منفی ۳۱٫۸٪» or «۳۱٫۸٪». */
    static String signed(Double value) {
        return value == null ? "نامعلوم" : (value < 0 ? "منفی " : "") + plain(value);
    }

    static String digits(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }

    private static String join(List<String> labels) {
        List<String> quoted = labels.stream().map(label -> "«" + label + "»").toList();
        if (quoted.size() == 1) {
            return quoted.get(0);
        }
        return String.join("، ", quoted.subList(0, quoted.size() - 1)) + " و " + quoted.get(quoted.size() - 1);
    }

    /** A percent in Persian digits: +۱۲٫۳٪ */
    static String pct(Double value) {
        if (value == null) {
            return "نامعلوم";
        }
        String text = String.format(Locale.ROOT, "%+.1f", value);
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            out.append(Character.isDigit(c) ? (char) ('۰' + (c - '0')) : c == '.' ? '٫' : c);
        }
        return out.append('٪').toString();
    }

    // ------------------------------------------------------------------ for Pouria's director and voice

    /** The analysis lines of [MARKET]: the same figures the report shows, for the companion to read like an analyst. */
    public String analysisBlock() {
        MarketDataService.Snapshot now = market.snapshot();
        if (now.quotes().isEmpty()) {
            return "";
        }
        Map<String, Object> report = build(now, reference.current(), LocalDate.now(MarketDataService.TEHRAN));
        StringBuilder out = new StringBuilder("ANALYSIS (computed from a year of daily closes; describe the past, never forecast):\n");
        for (Object item : (List<?>) report.get("assets")) {
            Map<?, ?> asset = (Map<?, ?>) item;
            Map<?, ?> changes = (Map<?, ?>) asset.get("changes");
            out.append("- ").append(asset.get("label"))
                    .append(" | 3 months ").append(MarketAnalysis.percent((Double) changes.get("quarter")))
                    .append(" | 6 months ").append(MarketAnalysis.percent((Double) changes.get("half")))
                    .append(" | 1 year ").append(MarketAnalysis.percent((Double) changes.get("year")))
                    .append(" | yearly volatility ").append(number(asset.get("volatility")))
                    .append(" | deepest fall in the year ").append(MarketAnalysis.percent((Double) asset.get("maxDrawdown")))
                    .append(" | below the year's high ").append(MarketAnalysis.percent((Double) asset.get("fromHigh")))
                    .append(" | trend vs 50/200-day averages ").append(asset.get("trend") == null ? "unknown" : asset.get("trend"))
                    .append(asset.get("realYear") == null ? "" : " | 1 year after inflation " + MarketAnalysis.percent((Double) asset.get("realYear")))
                    .append(asset.get("premium") == null ? "" : " | premium over its gold " + MarketAnalysis.percent((Double) asset.get("premium")))
                    .append('\n');
        }
        Map<?, ?> deposit = (Map<?, ?>) report.get("deposit");
        out.append("bank deposits (official caps, ").append(deposit.get("source")).append(", ").append(deposit.get("asOf")).append("): ");
        for (FinanceReferenceService.Rate rate : reference.depositRates()) {
            out.append(rate.term()).append(' ').append(rate.percent()).append("%; ");
        }
        if (deposit.get("realYear") != null) {
            out.append("one-year deposit after inflation ").append(MarketAnalysis.percent((Double) deposit.get("realYear")));
        }
        out.append(". Some banks pay more on large deposits in special schemes; above the cap is against the rule.\n");
        Map<?, ?> housing = (Map<?, ?>) report.get("housing");
        out.append("housing (").append(housing.get("source")).append(", ").append(housing.get("asOf")).append("): Tehran ")
                .append(housing.get("tehran")).append("; country ").append(housing.get("country"))
                .append(". No official monthly series; unofficial figures differ widely; one neighbourhood differs a lot from the city's average.\n");
        out.append("plain sentences of the report (Persian, may be used as they are):\n");
        for (Object line : (List<?>) report.get("insights")) {
            out.append("* ").append(line).append('\n');
        }
        out.append("HOW TO USE: read these like a professional analyst: compare returns over spans, after inflation, with their volatility and deepest falls; "
                + "explain the coin premium and the deposit's real rate; relate it to their own goal and time. Never forecast a price, never say buy or sell, "
                + "never name a fund or a share; say the past does not promise the future. A full report with charts is the MARKET card.\n");
        return out.toString();
    }

    private static String number(Object value) {
        return value == null ? "unknown" : String.format(Locale.ROOT, "%.0f%%", (Double) value);
    }

    // ------------------------------------------------------------------ what people see

    /** The reports this person can open, newest first: theirs, and those of their conversations. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(User user) {
        Map<Long, String> rooms = new LinkedHashMap<>();
        for (ChatRoomParticipant participant : participants.findAllForUser(user.getId())) {
            rooms.put(participant.getChatRoom().getId(), participant.getChatRoom().getTitle());
        }
        List<Long> ids = rooms.isEmpty() ? List.of(-1L) : new ArrayList<>(rooms.keySet());
        return reports.findVisible(user.getId(), ids).stream().limit(60).map(report -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", report.getPublicId());
            item.put("createdAt", report.getCreatedAt());
            item.put("roomTitle", report.getChatRoomId() == null ? null : rooms.get(report.getChatRoomId()));
            JsonNode data = read(report);
            item.put("day", data.path("day").asText(""));
            item.put("headline", data.path("insights").path(0).asText(""));
            return item;
        }).toList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> view(String publicId, User user) {
        MarketReport report = reports.findByPublicId(publicId)
                .filter(found -> visible(found, user))
                .orElseThrow(() -> new IllegalArgumentException("این گزارش پیدا نشد."));
        Map<String, Object> out = JSON.convertValue(read(report), Map.class);
        out.put("id", report.getPublicId());
        out.put("createdAt", report.getCreatedAt());
        return out;
    }

    /** A report of today for this person, from the dashboard; one made in the last hour is given again. */
    @Transactional
    public Map<String, Object> createFor(User user) {
        if (!available(user)) {
            throw new IllegalArgumentException("گزارش بازار برای کسانی است که در جلسه با پوریا گزارش گرفته‌اند.");
        }
        MarketReport recent = reports.findTopByUserIdOrderByIdDesc(user.getId())
                .filter(report -> report.getCreatedAt() != null
                        && report.getCreatedAt().isAfter(java.time.LocalDateTime.now().minus(REUSE)))
                .orElse(null);
        MarketReport report = recent != null ? recent : create(null, user.getId());
        if (report == null) {
            throw new IllegalArgumentException("الان قیمت تازه‌ای از بازار در دست نیست؛ کمی بعد دوباره امتحان کن.");
        }
        return view(report.getPublicId(), user);
    }

    /**
     * Whether «گزارش‌های مالی» is open to this person: Pouria gave a report in a session of one of their
     * conversations. Until then there is no tab, and no report from the dashboard either.
     */
    @Transactional(readOnly = true)
    public boolean available(User user) {
        List<Long> rooms = participants.findAllForUser(user.getId()).stream()
                .map(participant -> participant.getChatRoom().getId()).toList();
        return !rooms.isEmpty() && reports.existsByChatRoomIdIn(rooms);
    }

    private boolean visible(MarketReport report, User user) {
        if (Objects.equals(report.getUserId(), user.getId())) {
            return true;
        }
        return report.getChatRoomId() != null && participants.existsByChatRoom_IdAndUser_Id(report.getChatRoomId(), user.getId());
    }

    private static JsonNode read(MarketReport report) {
        try {
            return JSON.readTree(report.getDataJson());
        } catch (Exception broken) {
            return JSON.createObjectNode();
        }
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        reports.deleteAllByChatRoomId(roomId);
    }
}
