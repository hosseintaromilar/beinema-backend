package com.coupleai.coupleai.beinema.market;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What an analyst reads off a price history, computed by code so the same prices always give the
 * same figures: returns over several spans, volatility, the deepest fall of the year, where the price
 * stands in its year, its trend against the 50- and 200-day averages, the return after inflation, and
 * how far a coin is above the gold in it. All of it describes the past; none of it forecasts.
 */
public final class MarketAnalysis {

    /** One gram in a troy ounce. */
    static final double OUNCE = 31.1035;

    /** Gold in a coin, in grams of pure gold (the coin is 21.6 carat, 0.9 pure). */
    static final Map<String, Double> COIN_GOLD = Map.of(
            "سکهٔ امامی", 8.133 * 0.9,
            "نیم‌سکه", 4.0665 * 0.9,
            "ربع‌سکه", 2.03275 * 0.9,
            "طلای ۱۸ عیار، هر گرم", 0.75);

    /** Spans of the returns, in days. */
    static final Map<String, Integer> SPANS = new LinkedHashMap<>();

    static {
        SPANS.put("week", 7);
        SPANS.put("month", 30);
        SPANS.put("quarter", 91);
        SPANS.put("half", 182);
        SPANS.put("year", 365);
    }

    /**
     * One market read like an analyst would.
     *
     * @param changes      percent change over each span of SPANS (null when the history is too short)
     * @param volatility   annualized standard deviation of daily returns over the last 90 days, percent
     * @param maxDrawdown  the deepest fall from a peak within the last year, percent (zero or negative)
     * @param fromHigh     how far below the year's highest close the price is, percent (zero or negative)
     * @param rangePlace   where the price stands between the year's low (0) and high (100)
     * @param trend        UP: above both averages, DOWN: below both, MIXED otherwise; null without enough history
     * @param realYear     the year's return after the official inflation, percent
     */
    public record Analysis(String label, String unit, double price, Map<String, Double> changes, Double volatility,
                           Double maxDrawdown, Double fromHigh, Double rangePlace, Double average50, Double average200,
                           String trend, Double realYear, Double yearHigh, Double yearLow) {
    }

    private MarketAnalysis() {
    }

    /** series: [epoch day, price] oldest first, in the unit shown; price: now. */
    public static Analysis of(String label, String unit, double price, List<double[]> series, LocalDate today,
                              Double inflationPercent) {
        List<double[]> points = withToday(series, today, price);
        Map<String, Double> changes = new LinkedHashMap<>();
        SPANS.forEach((name, days) -> changes.put(name, change(price, closeOnOrBefore(points, today.minusDays(days), days))));

        List<double[]> year = since(points, today.minusDays(365));
        Double high = null;
        Double low = null;
        Double drawdown = null;
        if (year.size() >= 20) {
            double peak = 0;
            double worst = 0;
            high = Double.NEGATIVE_INFINITY;
            low = Double.POSITIVE_INFINITY;
            for (double[] point : year) {
                peak = Math.max(peak, point[1]);
                worst = Math.min(worst, (point[1] - peak) / peak * 100);
                high = Math.max(high, point[1]);
                low = Math.min(low, point[1]);
            }
            drawdown = worst;
        }
        Double fromHigh = high == null ? null : Math.min(0, (price - high) / high * 100);
        Double place = high == null || high.equals(low) ? null : Math.max(0, Math.min(100, (price - low) / (high - low) * 100));

        Double average50 = average(points, 50);
        Double average200 = average(points, 200);
        String trend = null;
        if (average50 != null && average200 != null) {
            trend = price > average50 && price > average200 ? "UP" : price < average50 && price < average200 ? "DOWN" : "MIXED";
        }
        Double yearChange = changes.get("year");
        Double real = yearChange == null || inflationPercent == null ? null
                : ((1 + yearChange / 100) / (1 + inflationPercent / 100) - 1) * 100;
        return new Analysis(label, unit, price, changes, volatility(points, today), drawdown, fromHigh, place,
                average50, average200, trend, real, high, low);
    }

    /** The coin (or gram of 18-carat gold) over the gold in it, at the world price and the free dollar: percent. */
    public static Double premium(String label, double price, Double ounceDollars, Double dollarTomans) {
        Double gold = COIN_GOLD.get(label);
        if (gold == null || ounceDollars == null || dollarTomans == null || ounceDollars <= 0 || dollarTomans <= 0) {
            return null;
        }
        double worth = ounceDollars / OUNCE * gold * dollarTomans;
        return (price - worth) / worth * 100;
    }

    /** A deposit's rate after inflation, percent. */
    public static Double realRate(double ratePercent, Double inflationPercent) {
        return inflationPercent == null ? null : ((1 + ratePercent / 100) / (1 + inflationPercent / 100) - 1) * 100;
    }

    /**
     * 100 tomans put in each market a year ago (or as far back as all of them go): what it became over time,
     * weekly, next to a deposit at its rate and to prices rising with inflation. [epoch day, value] per name.
     */
    public static Map<String, List<double[]>> growthOf100(Map<String, List<double[]>> series, LocalDate today,
                                                          Double depositPercent, Double inflationPercent) {
        Map<String, List<double[]>> out = new LinkedHashMap<>();
        long start = today.minusDays(365).toEpochDay();
        for (Map.Entry<String, List<double[]>> entry : series.entrySet()) {
            Double base = closeOnOrBefore(entry.getValue(), LocalDate.ofEpochDay(start), 365);
            if (base == null || base <= 0) {
                continue;
            }
            List<double[]> line = new ArrayList<>();
            for (long day = start; day <= today.toEpochDay(); day += 7) {
                Double close = closeOnOrBefore(entry.getValue(), LocalDate.ofEpochDay(day), 10);
                if (close != null) {
                    line.add(new double[]{day, close / base * 100});
                }
            }
            Double last = closeOnOrBefore(entry.getValue(), today, 10);
            if (last != null && (line.isEmpty() || line.get(line.size() - 1)[0] < today.toEpochDay())) {
                line.add(new double[]{today.toEpochDay(), last / base * 100});
            }
            out.put(entry.getKey(), line);
        }
        if (depositPercent != null) {
            out.put("سپردهٔ یک‌ساله", compound(start, today.toEpochDay(), depositPercent));
        }
        if (inflationPercent != null) {
            out.put("تورم", compound(start, today.toEpochDay(), inflationPercent));
        }
        return out;
    }

    private static List<double[]> compound(long start, long end, double percent) {
        List<double[]> line = new ArrayList<>();
        for (long day = start; day <= end; day += 7) {
            line.add(new double[]{day, 100 * Math.pow(1 + percent / 100, (day - start) / 365.0)});
        }
        line.add(new double[]{end, 100 * Math.pow(1 + percent / 100, (end - start) / 365.0)});
        return line;
    }

    /** At most about max points, evenly over the last year, the newest always kept. */
    public static List<double[]> thin(List<double[]> series, LocalDate today, int max) {
        List<double[]> year = since(series, today.minusDays(366));
        if (year.size() <= max) {
            return year;
        }
        List<double[]> out = new ArrayList<>();
        double step = (double) year.size() / max;
        for (int i = 0; i < max; i++) {
            out.add(year.get((int) Math.floor(i * step)));
        }
        out.set(out.size() - 1, year.get(year.size() - 1));
        return out;
    }

    // ------------------------------------------------------------------ the parts

    static List<double[]> withToday(List<double[]> series, LocalDate today, double price) {
        List<double[]> points = new ArrayList<>();
        for (double[] point : series) {
            if (point[1] > 0 && point[0] < today.toEpochDay()) {
                points.add(point);
            }
        }
        points.sort((a, b) -> Double.compare(a[0], b[0]));
        points.add(new double[]{today.toEpochDay(), price});
        return points;
    }

    static List<double[]> since(List<double[]> series, LocalDate from) {
        return series.stream().filter(point -> point[0] >= from.toEpochDay()).toList();
    }

    /** The close of the latest day on or before the day, no more than slack days before it. */
    static Double closeOnOrBefore(List<double[]> closes, LocalDate day, int slack) {
        double[] best = null;
        for (double[] close : closes) {
            if (close[0] <= day.toEpochDay() && close[1] > 0 && (best == null || close[0] > best[0])) {
                best = close;
            }
        }
        if (best == null || day.toEpochDay() - best[0] > Math.max(7, slack / 10)) {
            return null;
        }
        return best[1];
    }

    static Double change(double now, Double then) {
        return then == null || then <= 0 ? null : (now - then) / then * 100;
    }

    static Double average(List<double[]> points, int count) {
        if (points.size() < count) {
            return null;
        }
        double sum = 0;
        for (int i = points.size() - count; i < points.size(); i++) {
            sum += points.get(i)[1];
        }
        return sum / count;
    }

    /** Annualized, from the daily returns of the last 90 days; scaled by how many closes a year this market has. */
    static Double volatility(List<double[]> points, LocalDate today) {
        List<double[]> recent = since(points, today.minusDays(90));
        if (recent.size() < 30) {
            return null;
        }
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < recent.size(); i++) {
            returns.add(Math.log(recent.get(i)[1] / recent.get(i - 1)[1]));
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream().mapToDouble(value -> (value - mean) * (value - mean)).sum() / (returns.size() - 1);
        double perYear = recent.size() / 90.0 * 365;
        return Math.sqrt(variance * perYear) * 100;
    }

    static String percent(Double value) {
        return value == null ? "unknown" : String.format(Locale.ROOT, "%+.1f%%", value);
    }
}
