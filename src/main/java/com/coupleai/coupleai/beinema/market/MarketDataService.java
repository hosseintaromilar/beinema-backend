package com.coupleai.coupleai.beinema.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Today's prices of the markets a couple usually weighs (currency, gold and coins, the Tehran stock
 * index, bitcoin), with the change of the day, the week and the month, for the financial companion.
 * Read every hour from public sources (tgju.org, tsetmc.com) and kept in memory. The companion gets
 * the figures with their source and time; when they are old or missing the block says so, so the
 * brain never makes a number up.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    /** Older than this, the figures are shown as stale. */
    static final Duration FRESH = Duration.ofHours(6);
    private static final String TGJU_NOW = "https://call.tgju.org/ajax.json";
    private static final String TGJU_HISTORY = "https://api.tgju.org/v1/market/indicator/summary-table-data/%s?length=40";
    private static final String INDEX_NOW = "https://cdn.tsetmc.com/api/Index/GetIndexB1LastAll/SelectedIndexes/1";
    private static final String INDEX_HISTORY = "https://cdn.tsetmc.com/api/Index/GetIndexB2History/32097828799138957";
    private static final String INDEX_CODE = "32097828799138957";

    /** What is shown: tgju key, Persian label, unit, whether the price is in rial (shown in toman), and a history. */
    record Item(String key, String label, String unit, boolean rial, boolean history) {
    }

    static final List<Item> ITEMS = List.of(
            new Item("price_dollar_rl", "دلار آزاد", "تومان", true, true),
            new Item("price_eur", "یورو", "تومان", true, false),
            new Item("sekee", "سکهٔ امامی", "تومان", true, true),
            new Item("nim", "نیم‌سکه", "تومان", true, false),
            new Item("rob", "ربع‌سکه", "تومان", true, false),
            new Item("geram18", "طلای ۱۸ عیار، هر گرم", "تومان", true, true),
            new Item("ons", "انس جهانی طلا", "دلار", false, false),
            new Item("crypto-bitcoin", "بیت‌کوین", "دلار", false, false));

    /** One market's line: price and changes in percent (null when unknown). */
    public record Quote(String label, double price, String unit, Double day, Double week, Double month) {
    }

    /** The prices as they are now, and when they were read (null: never). */
    public record Snapshot(List<Quote> quotes, Instant updatedAt) {
        public boolean stale(Instant now) {
            return updatedAt == null || Duration.between(updatedAt, now).compareTo(FRESH) > 0;
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(quotes, updatedAt);
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${beinema.market.enabled:true}")
    private boolean enabled;

    private volatile List<Quote> quotes = List.of();
    private volatile Instant updatedAt;

    @Scheduled(initialDelayString = "${beinema.market.initial-delay-ms:20000}", fixedDelayString = "${beinema.market.refresh-ms:3600000}")
    public void refresh() {
        if (!enabled) {
            return;
        }
        try {
            List<Quote> fresh = new ArrayList<>();
            JsonNode now = get(TGJU_NOW).path("current");
            LocalDate today = LocalDate.now(TEHRAN);
            for (Item item : ITEMS) {
                JsonNode node = now.path(item.key());
                double price = number(node.path("p").asText(""));
                if (price <= 0) {
                    continue;
                }
                double shown = item.rial() ? price / 10 : price;
                double day = node.path("dp").asDouble(0) * ("low".equals(node.path("dt").asText()) ? -1 : 1);
                Double week = null;
                Double month = null;
                if (item.history()) {
                    List<double[]> closes = tgjuHistory(item.key());
                    week = change(price, closeOnOrBefore(closes, today.minusDays(7)));
                    month = change(price, closeOnOrBefore(closes, today.minusDays(30)));
                }
                fresh.add(new Quote(item.label(), shown, item.unit(), day, week, month));
            }
            Quote index = stockIndex(today);
            if (index != null) {
                fresh.add(index);
            }
            if (!fresh.isEmpty()) {
                quotes = List.copyOf(fresh);
                updatedAt = Instant.now();
            }
        } catch (Exception failure) {
            log.warn("Market prices could not be read: {}", failure.getMessage());
        }
    }

    /** The [MARKET] block of the financial companion's packet. */
    public String block() {
        return block(quotes, updatedAt, Instant.now());
    }

    static String block(List<Quote> quotes, Instant updatedAt, Instant now) {
        if (quotes.isEmpty() || updatedAt == null) {
            return "unavailable: no market prices could be read now. Say so plainly if they ask; never give a price from memory.\n";
        }
        ZonedDateTime at = updatedAt.atZone(TEHRAN);
        StringBuilder out = new StringBuilder();
        out.append("source: tgju.org (currency, gold, coins, bitcoin), tsetmc.com (stock index)\n");
        out.append("updated: ").append(jalali(at.toLocalDate())).append(' ')
                .append(at.format(DateTimeFormatter.ofPattern("HH:mm"))).append(" Tehran time\n");
        if (Duration.between(updatedAt, now).compareTo(FRESH) > 0) {
            out.append("STALE: these figures are older than six hours; say they may have changed since then.\n");
        }
        out.append("changes are in percent: today, about one week, about one month (- means down)\n");
        for (Quote quote : quotes) {
            out.append("- ").append(quote.label()).append(": ").append(amount(quote.price())).append(' ').append(quote.unit())
                    .append(" | today ").append(percent(quote.day()))
                    .append(" | week ").append(percent(quote.week()))
                    .append(" | month ").append(percent(quote.month())).append('\n');
        }
        out.append("not here: bank deposit rates, inflation, housing prices. Never give a figure for them from memory; say they need checking with an official source.\n");
        return out.toString();
    }

    private Quote stockIndex(LocalDate today) {
        try {
            JsonNode now = null;
            for (JsonNode node : get(INDEX_NOW).path("indexB1")) {
                if (INDEX_CODE.equals(node.path("insCode").asText())) {
                    now = node;
                }
            }
            if (now == null) {
                return null;
            }
            double value = now.path("xDrNivJIdx004").asDouble(0);
            if (value <= 0) {
                return null;
            }
            List<double[]> closes = new ArrayList<>();
            for (JsonNode day : get(INDEX_HISTORY).path("indexB2")) {
                String even = day.path("dEven").asText("");
                if (even.length() == 8) {
                    LocalDate date = LocalDate.of(Integer.parseInt(even.substring(0, 4)),
                            Integer.parseInt(even.substring(4, 6)), Integer.parseInt(even.substring(6, 8)));
                    closes.add(new double[]{date.toEpochDay(), day.path("xNivInuClMresIbs").asDouble(0)});
                }
            }
            return new Quote("شاخص کل بورس تهران", value, "واحد", now.path("xVarIdxJRfV").asDouble(0),
                    change(value, closeOnOrBefore(closes, today.minusDays(7))),
                    change(value, closeOnOrBefore(closes, today.minusDays(30))));
        } catch (Exception failure) {
            log.warn("The stock index could not be read: {}", failure.getMessage());
            return null;
        }
    }

    /** Daily closes of one tgju market: [epoch day, close in rial]. */
    private List<double[]> tgjuHistory(String key) {
        List<double[]> closes = new ArrayList<>();
        try {
            for (JsonNode row : get(String.format(TGJU_HISTORY, key)).path("data")) {
                String[] date = row.path(6).asText("").split("/");
                if (date.length == 3) {
                    LocalDate day = LocalDate.of(Integer.parseInt(date[0]), Integer.parseInt(date[1]), Integer.parseInt(date[2]));
                    closes.add(new double[]{day.toEpochDay(), number(row.path(3).asText(""))});
                }
            }
        } catch (Exception failure) {
            log.warn("History of {} could not be read: {}", key, failure.getMessage());
        }
        return closes;
    }

    /** The close of the latest day on or before the given day (the market may have been shut that day). */
    static Double closeOnOrBefore(List<double[]> closes, LocalDate day) {
        double[] best = null;
        for (double[] close : closes) {
            if (close[0] <= day.toEpochDay() && close[1] > 0 && (best == null || close[0] > best[0])) {
                best = close;
            }
        }
        return best == null ? null : best[1];
    }

    static Double change(double now, Double then) {
        return then == null || then <= 0 ? null : (now - then) / then * 100;
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (compatible; Beinema)")
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(url + " answered " + response.statusCode());
        }
        return json.readTree(response.body());
    }

    static double number(String text) {
        String digits = text == null ? "" : text.replace(",", "").trim();
        try {
            return digits.isEmpty() ? 0 : Double.parseDouble(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static String amount(double value) {
        return value >= 1000
                ? String.format(Locale.ROOT, "%,.0f", value)
                : String.format(Locale.ROOT, "%,.2f", value);
    }

    static String percent(Double value) {
        if (value == null) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%+.1f%%", value);
    }

    /** A Gregorian day as a Jalali date, 1405/08/16. */
    static String jalali(LocalDate day) {
        int gy = day.getYear();
        int gm = day.getMonthValue();
        int gd = day.getDayOfMonth();
        int[] before = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = gm > 2 ? gy + 1 : gy;
        long days = 355666 + 365L * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + before[gm - 1];
        long jy = -1595 + 33 * (days / 12053);
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm;
        int jd;
        if (days < 186) {
            jm = 1 + (int) (days / 31);
            jd = 1 + (int) (days % 31);
        } else {
            jm = 7 + (int) ((days - 186) / 30);
            jd = 1 + (int) ((days - 186) % 30);
        }
        return String.format(Locale.ROOT, "%d/%02d/%02d", jy, jm, jd);
    }
}
