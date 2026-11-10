package com.coupleai.coupleai.beinema.counseling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The financial companion's own material: the risk quiz, the household's financial picture and
 * the plan of scenarios. Everything here is computed by code, not by the model, so the same
 * answers always give the same profile and the same scenarios. Pouria explains and asks; the
 * people decide.
 *
 * The quiz measures two things, as advisers do: willingness (how a person feels and acts about
 * losing and gaining) and capacity (how much loss their life can actually bear). The profile is
 * the lower of the two. It is written for Beinema, after the ideas of common risk-tolerance
 * questionnaires (such as Grable and Lytton), not a copy of any of them.
 */
public final class FinanceKit {

    public static final String TAG = "FIN";
    public static final String WILL = "WILL";
    public static final String CAP = "CAP";

    public static final String SAFE = "SAFE";
    public static final String CAUTIOUS = "CAUTIOUS";
    public static final String BALANCED = "BALANCED";
    public static final String GROWTH = "GROWTH";
    static final List<String> LEVELS = List.of(SAFE, CAUTIOUS, BALANCED, GROWTH);

    public static final String RISK_TITLE = "آزمونک ریسک‌پذیری";
    public static final String RISK_INTRO = "ده سؤال کوتاه دربارهٔ اینکه با بالا و پایین شدن پول چه حسی داری و زندگیت چقدر تحملش رو داره. "
            + "جواب درست و غلط نداره؛ هر کس جدا جواب می‌ده. نتیجه نمره یا توصیهٔ سرمایه‌گذاری نیست، فقط تصویر امروز خودته.";

    public record Item(String id, String dim, String label, String prompt, List<String> options) {
    }

    /** Each option is worth its position: 0 is the most careful answer, 3 the boldest. */
    public static final List<Item> RISK_ITEMS = List.of(
            new Item("W_DROP", WILL, "واکنش به ضرر", "فرض کن پس‌اندازت توی سه ماه ۲۰ درصد کم شد. چی کار می‌کنی؟",
                    List.of("همه‌ش رو می‌فروشم که بیشتر ضرر نکنم", "یه بخشش رو می‌فروشم", "صبر می‌کنم تا برگرده", "بیشتر می‌خرم، چون ارزون شده")),
            new Item("W_CHOICE", WILL, "سود قطعی یا شانس", "کدوم رو انتخاب می‌کنی؟",
                    List.of("۲۰ میلیون سود قطعی", "۸۰ درصد شانس ۳۰ میلیون", "۵۰ درصد شانس ۶۰ میلیون", "۲۰ درصد شانس ۱۵۰ میلیون")),
            new Item("W_WORD", WILL, "معنی ریسک", "وقتی به «ریسک» فکر می‌کنی، اول چی به ذهنت میاد؟",
                    List.of("ضرر", "نگرانی", "فرصت", "هیجان")),
            new Item("W_SLEEP", WILL, "آرامش با نوسان", "اگه پولت جایی باشه که قیمتش هر روز بالا و پایین می‌ره، چقدر آرومی؟",
                    List.of("اصلاً آروم نیستم", "بیشتر وقت‌ها نگرانم", "تا وقتی بلندمدته اذیت نمی‌شم", "برام مهم نیست")),
            new Item("W_PAST", WILL, "تجربه", "با سرمایه‌گذاری پرنوسان (بورس، ارز، رمزارز) چه تجربه‌ای داری؟",
                    List.of("هیچ‌وقت سراغش نرفتم", "رفتم و ضرری کردم که هنوز اذیتم می‌کنه", "یه کم داشتم و قابل تحمل بود", "زیاد داشتم و بالا و پایینش رو می‌شناسم")),
            new Item("W_REGRET", WILL, "ضرر یا جاموندن", "کدوم بیشتر اذیتت می‌کنه؟",
                    List.of("از دست دادن پولی که داشتم", "هر دو، ولی ضرر بیشتر", "هر دو، ولی جاموندن بیشتر", "جاموندن از سودی که بقیه بردن")),
            new Item("C_HORIZON", CAP, "زمان نیاز به پول", "این پول رو کی لازم دارید؟",
                    List.of("کمتر از یه سال", "یک تا سه سال", "سه تا پنج سال", "بیشتر از پنج سال")),
            new Item("C_RESERVE", CAP, "پشتوانهٔ اضطراری", "اگه درآمد قطع بشه، پس‌اندازتون هزینهٔ ضروری چند ماه رو می‌ده؟",
                    List.of("کمتر از یه ماه", "یک تا سه ماه", "سه تا شش ماه", "بیشتر از شش ماه")),
            new Item("C_INCOME", CAP, "ثبات درآمد", "درآمدت چقدر ثابته؟",
                    List.of("نامنظم یا در خطر", "تا حدی ثابت", "ثابت", "ثابت و از چند منبع")),
            new Item("C_LOSS", CAP, "اثر ضرر بر زندگی", "اگه بخشی از این پول از دست بره، زندگیتون چقدر عوض می‌شه؟",
                    List.of("خیلی؛ به خرج‌های ضروری می‌خوره", "باید از چیزهای مهم بزنیم", "کمی سخت می‌شه", "تقریباً اثری نداره")));

    /** One person's result: two scores from 0 to 100, the profile (the lower of the two) and a mismatch. */
    public record Risk(int willingness, int capacity, String profile, String willingnessLevel, String capacityLevel,
                       boolean mismatch) {
    }

    public static boolean complete(Map<String, Integer> answers) {
        if (answers == null) {
            return false;
        }
        for (Item item : RISK_ITEMS) {
            Integer value = answers.get(item.id());
            if (value == null || value < 0 || value >= item.options().size()) {
                return false;
            }
        }
        return true;
    }

    public static Risk risk(Map<String, Integer> answers) {
        int will = 0;
        int willMax = 0;
        int cap = 0;
        int capMax = 0;
        for (Item item : RISK_ITEMS) {
            int value = answers.getOrDefault(item.id(), 0);
            int max = item.options().size() - 1;
            if (WILL.equals(item.dim())) {
                will += value;
                willMax += max;
            } else {
                cap += value;
                capMax += max;
            }
        }
        int willingness = Math.round(100f * will / willMax);
        int capacity = Math.round(100f * cap / capMax);
        String willLevel = level(willingness);
        String capLevel = level(capacity);
        String profile = LEVELS.indexOf(willLevel) <= LEVELS.indexOf(capLevel) ? willLevel : capLevel;
        // says they like a gamble but would sell everything after a fall, or the other way round
        int drop = answers.getOrDefault("W_DROP", 0);
        int choice = answers.getOrDefault("W_CHOICE", 0);
        boolean mismatch = (drop == 0 && choice >= 2) || (drop == 3 && choice == 0);
        return new Risk(willingness, capacity, profile, willLevel, capLevel, mismatch);
    }

    static String level(int score) {
        if (score < 25) {
            return SAFE;
        }
        if (score < 50) {
            return CAUTIOUS;
        }
        if (score < 75) {
            return BALANCED;
        }
        return GROWTH;
    }

    public static String profileTitle(String profile) {
        return switch (profile) {
            case SAFE -> "خیلی محتاط";
            case CAUTIOUS -> "محتاط";
            case BALANCED -> "متعادل";
            default -> "رشدطلب";
        };
    }

    public static String profileText(String profile) {
        return switch (profile) {
            case SAFE -> "حفظ اصل پول برات از هر سودی مهم‌تره. نوسان زیاد نه با حالت جوره، نه با شرایط زندگیت.";
            case CAUTIOUS -> "کمی نوسان رو تحمل می‌کنی، ولی امنیت پول برات اولویته. سود آروم و مطمئن رو به سود زیاد و پرریسک ترجیح می‌دی.";
            case BALANCED -> "برای سود بیشتر بالا و پایین رو قبول داری، به شرطی که همه‌ش یه‌جا نباشه و زمان داشته باشی.";
            default -> "برای رشد بیشتر، نوسان و حتی ضرر موقت رو می‌پذیری و زمان و پشتوانه‌ش رو هم داری.";
        };
    }

    // ------------------------------------------------------------------ the financial picture

    /** A field of the picture, in million tomans; HOUSING is a choice. */
    public record Field(String id, String label, String group) {
    }

    public static final List<Field> FIELDS = List.of(
            new Field("INCOME", "درآمد ماهانهٔ خانواده (همهٔ منابع)", "MONTH"),
            new Field("ESSENTIAL", "هزینهٔ ضروری ماهانه (اجاره، خوراک، قبض، رفت‌وآمد)", "MONTH"),
            new Field("OTHER", "بقیهٔ هزینه‌های ماهانه", "MONTH"),
            new Field("INSTALMENTS", "قسط‌های ماهانه", "MONTH"),
            new Field("DEBTS", "کل بدهی باقی‌مانده (وام، قرض، چک)", "DEBT"),
            new Field("CASH", "پول نقد و سپردهٔ بانکی", "ASSET"),
            new Field("GOLD", "طلا و سکه (ارزش تقریبی)", "ASSET"),
            new Field("CURRENCY", "ارز", "ASSET"),
            new Field("STOCKS", "بورس و صندوق‌ها", "ASSET"),
            new Field("PROPERTY", "ملک (ارزش تقریبی)", "ASSET"),
            new Field("CAR", "خودرو", "ASSET"),
            new Field("OTHER_ASSETS", "دارایی‌های دیگر", "ASSET"),
            new Field("GOAL_AMOUNT", "پولی که برای هدفتون لازم دارید (به قیمت امروز)", "GOAL"),
            new Field("GOAL_MONTHS", "چند ماه دیگه لازمش دارید (عدد ماه)", "GOAL"));

    public static final Map<String, String> HOUSING = Map.of("RENT", "مستأجر", "OWN", "مالک", "FAMILY", "با خانواده");

    /** What the picture says, from the figures they gave (million tomans; null where it cannot be known). */
    public record Picture(Double surplus, Double savingRate, Double instalmentShare, Double reserveMonths, Double netWorth,
                          Double financialAssets, Map<String, Double> mix, String capacityLevel, String capacityWhy,
                          Goal goal) {
    }

    /**
     * Whether the goal can be reached by saving alone, in today's money: what they have now plus the
     * monthly surplus until the deadline. gap > 0: it cannot; then three honest ways out, never more risk.
     */
    public record Goal(double amount, int months, double projected, double gap, Double extraMonthly, Integer monthsNeeded) {
        public boolean reachable() {
            return gap <= 0;
        }
    }

    /**
     * How much loss their life can bear, from their own figures rather than from how they feel:
     * no surplus or almost no reserve is SAFE; a thin reserve or heavy instalments CAUTIOUS; a full
     * reserve with real saving and light instalments GROWTH; otherwise BALANCED. Null when the
     * figures that decide it are missing.
     */
    static String[] capacity(Double surplus, Double savingRate, Double instalmentShare, Double reserveMonths) {
        if (surplus == null || reserveMonths == null) {
            return new String[]{null, ""};
        }
        if (surplus < 0) {
            return new String[]{SAFE, "monthly spending is more than income"};
        }
        if (reserveMonths < 1) {
            return new String[]{SAFE, "less than one month of essential costs in cash"};
        }
        if (reserveMonths < 3) {
            return new String[]{CAUTIOUS, "less than three months of essential costs in cash"};
        }
        if (instalmentShare != null && instalmentShare > 0.4) {
            return new String[]{CAUTIOUS, "instalments take more than 40% of income"};
        }
        if (reserveMonths >= 6 && savingRate != null && savingRate >= 0.2 && (instalmentShare == null || instalmentShare <= 0.2)) {
            return new String[]{GROWTH, "a full reserve, real monthly saving and light instalments"};
        }
        return new String[]{BALANCED, "a reserve in place and a positive surplus"};
    }

    /** A deadline sets the boldest path that still fits it, whatever the quiz says. Null: no deadline given. */
    static String horizonCap(Integer months) {
        if (months == null || months <= 0) {
            return null;
        }
        return months < 12 ? SAFE : months < 36 ? CAUTIOUS : months < 60 ? BALANCED : GROWTH;
    }

    static Goal goal(Map<String, Double> v, Double surplus) {
        Double amount = v.get("GOAL_AMOUNT");
        Double rawMonths = v.get("GOAL_MONTHS");
        if (amount == null || amount <= 0 || rawMonths == null || rawMonths < 1) {
            return null;
        }
        int months = (int) Math.round(rawMonths);
        double have = v.getOrDefault("CASH", 0d) + v.getOrDefault("GOLD", 0d) + v.getOrDefault("CURRENCY", 0d)
                + v.getOrDefault("STOCKS", 0d);
        double monthly = surplus == null ? 0 : Math.max(0, surplus);
        double projected = have + monthly * months;
        double gap = amount - projected;
        Double extraMonthly = gap > 0 ? gap / months : null;
        Integer monthsNeeded = gap > 0 && monthly > 0 ? (int) Math.ceil((amount - have) / monthly) : null;
        return new Goal(amount, months, projected, gap, extraMonthly, monthsNeeded);
    }

    static String lower(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return LEVELS.indexOf(a) <= LEVELS.indexOf(b) ? a : b;
    }

    public static Picture picture(Map<String, Double> v) {
        Double income = v.get("INCOME");
        Double essential = v.get("ESSENTIAL");
        double other = v.getOrDefault("OTHER", 0d);
        double instalments = v.getOrDefault("INSTALMENTS", 0d);
        Double surplus = income == null || essential == null ? null : income - essential - other - instalments;
        Double savingRate = surplus == null || income <= 0 ? null : surplus / income;
        Double instalmentShare = income == null || income <= 0 ? null : instalments / income;
        double cash = v.getOrDefault("CASH", 0d);
        Double reserveMonths = essential == null || essential <= 0 ? null : cash / essential;
        double assets = 0;
        boolean anyAsset = false;
        for (Field field : FIELDS) {
            if ("ASSET".equals(field.group()) && v.get(field.id()) != null) {
                assets += v.get(field.id());
                anyAsset = true;
            }
        }
        Double netWorth = anyAsset || v.get("DEBTS") != null ? assets - v.getOrDefault("DEBTS", 0d) : null;
        Map<String, Double> mix = new LinkedHashMap<>();
        double financial = 0;
        for (String id : List.of("CASH", "GOLD", "CURRENCY", "STOCKS")) {
            financial += v.getOrDefault(id, 0d);
        }
        if (financial > 0) {
            for (String id : List.of("CASH", "GOLD", "CURRENCY", "STOCKS")) {
                mix.put(id, v.getOrDefault(id, 0d) / financial * 100);
            }
        }
        String[] capacity = capacity(surplus, savingRate, instalmentShare, reserveMonths);
        return new Picture(surplus, savingRate, instalmentShare, reserveMonths, netWorth, financial > 0 ? financial : null, mix,
                capacity[0], capacity[1], goal(v, surplus));
    }

    // ------------------------------------------------------------------ the plan

    /** A share of the money in one kind of asset: a range in percent and the middle of it for the chart. */
    public record Share(String id, String label, int from, int to) {
        public int middle() {
            return (from + to) / 2;
        }
    }

    /** badYear: what a bad year can do to it, plainly, so nobody chooses it for the good years only. */
    public record Scenario(String level, String title, String text, List<Share> shares, Map<String, Double> amounts,
                           String badYear) {
    }

    /** A rough fall in a bad year, in percent: an order of size to see, not a forecast. */
    static int[] badYearRange(String level) {
        return switch (level) {
            case SAFE -> new int[]{0, 5};
            case CAUTIOUS -> new int[]{5, 10};
            case BALANCED -> new int[]{15, 20};
            default -> new int[]{25, 35};
        };
    }

    static String badYear(String level, double investable) {
        int[] range = badYearRange(level);
        String text = "توی یه سال بد ممکنه ارزشش موقتاً حدود ٪" + range[0] + " تا ٪" + range[1] + " کم بشه";
        if (investable > 0 && range[1] > 0) {
            text += "، یعنی حدود " + amount(investable * range[0] / 100.0) + " تا " + amount(investable * range[1] / 100.0) + " میلیون";
        }
        return text + ". این پیش‌بینی نیست؛ اندازهٔ نوسانیه که باید تحملش رو داشته باشید.";
    }

    public record Step(String id, String title, String text) {
    }

    public record Plan(String profile, String note, List<Step> steps, double investable, List<Scenario> scenarios) {
    }

    static final Map<String, String> ASSET_LABELS = Map.of(
            "FIXED", "سپرده و صندوق درآمد ثابت",
            "GOLD", "طلا، سکه یا صندوق طلا",
            "CURRENCY", "ارز",
            "EQUITY", "صندوق سهامی و بورس");

    static List<Share> template(String level) {
        int[][] ranges = switch (level) {
            case SAFE -> new int[][]{{75, 85}, {15, 25}, {0, 0}, {0, 0}};
            case CAUTIOUS -> new int[][]{{60, 70}, {20, 30}, {0, 10}, {0, 10}};
            case BALANCED -> new int[][]{{35, 45}, {25, 35}, {5, 15}, {15, 25}};
            default -> new int[][]{{15, 25}, {25, 35}, {5, 15}, {35, 45}};
        };
        List<String> ids = List.of("FIXED", "GOLD", "CURRENCY", "EQUITY");
        List<Share> shares = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (ranges[i][1] > 0) {
                shares.add(new Share(ids.get(i), ASSET_LABELS.get(ids.get(i)), ranges[i][0], ranges[i][1]));
            }
        }
        return shares;
    }

    static String scenarioText(String level) {
        return switch (level) {
            case SAFE -> "بیشترش جایی که اصل پول حفظ می‌شه و سود ثابت می‌ده، کمی طلا برای جا نموندن از تورم. سود کمتر، خواب راحت‌تر.";
            case CAUTIOUS -> "پایه روی سود ثابته و طلا جلوی تورم رو می‌گیره؛ سهم کوچیکی هم برای رشد. نوسان کم.";
            case BALANCED -> "نیمی امن و نیمی رو به رشد. توی سال‌های بد افت داره، ولی برای سه تا پنج سال معقوله.";
            default -> "بیشتر رو به رشد؛ افت‌های تند موقت رو باید تحمل کرد و حداقل پنج سال زمان لازمه.";
        };
    }

    /**
     * The plan for the money they decide about together. The more careful profile of the two decides
     * for shared money. A step bolder than the profile is offered only when their capacity allows it.
     * risks: the result of each person who answered; capacityLevels: their capacity levels.
     */
    public static Plan plan(List<Risk> risks, Map<String, Double> v) {
        if (risks.isEmpty()) {
            return null;
        }
        String quizProfile = risks.stream().map(Risk::profile).min(java.util.Comparator.comparingInt(LEVELS::indexOf)).orElse(CAUTIOUS);
        String quizCapacity = risks.stream().map(Risk::capacityLevel).min(java.util.Comparator.comparingInt(LEVELS::indexOf)).orElse(CAUTIOUS);
        // three sides of risk, as advisers weigh it: how they feel (the quiz), what their figures can bear
        // (the picture) and what the deadline allows; the path is the most careful of them
        Picture picture = picture(v);
        Goal goal = picture.goal();
        String horizon = horizonCap(goal == null ? null : goal.months());
        String profile = lower(lower(quizProfile, picture.capacityLevel()), horizon);
        String capacity = lower(lower(quizCapacity, picture.capacityLevel()), horizon);
        List<String> notes = new ArrayList<>();
        if (risks.size() > 1 && risks.stream().map(Risk::profile).distinct().count() > 1) {
            notes.add("پروفایل‌هاتون فرق داره؛ برای پول مشترک، پروفایل محتاط‌تر ملاکه. برای سهم شخصی هرکدوم می‌تونید جدا تصمیم بگیرید.");
        }
        if (!profile.equals(quizProfile)) {
            notes.add(horizon != null && profile.equals(horizon) && !horizon.equals(picture.capacityLevel())
                    ? "چون این پول رو " + goal.months() + " ماه دیگه لازم دارید، مسیر محتاط‌تری از نتیجهٔ آزمونک‌تون مناسبه: پول کوتاه‌مدت نباید درگیر نوسان بشه."
                    : "عددهای تصویر مالی‌تون (" + capacityReason(picture.capacityWhy()) + ") می‌گه فعلاً توان تحمل ضرر کمتر از حسیه که توی آزمونک گفتید؛ برای همین مسیر محتاط‌تری اومده.");
        }
        String note = String.join(" ", notes);

        List<Step> steps = new ArrayList<>();
        Double essential = v.get("ESSENTIAL");
        double cash = v.getOrDefault("CASH", 0d);
        boolean shakyIncome = LEVELS.indexOf(capacity) <= LEVELS.indexOf(CAUTIOUS);
        int reserveMonths = shakyIncome ? 6 : 3;
        double reserve = essential == null ? 0 : essential * reserveMonths;
        if (essential != null && cash < reserve) {
            steps.add(new Step("RESERVE", "اول صندوق اضطراری",
                    "پشتوانهٔ " + reserveMonths + " ماه هزینهٔ ضروری، یعنی حدود " + amount(reserve) + " میلیون تومان، جایی که هر وقت لازم شد زود برسه. الان حدود " + amount(cash) + " میلیون دارید."));
        } else if (essential == null) {
            steps.add(new Step("RESERVE", "اول صندوق اضطراری",
                    "پشتوانهٔ " + reserveMonths + " ماه هزینهٔ ضروری کنار گذاشته بشه، بعد بقیه. با پر کردن تصویر مالی، عددش دقیق می‌شه."));
        }
        Double income = v.get("INCOME");
        double instalments = v.getOrDefault("INSTALMENTS", 0d);
        if ((income != null && income > 0 && instalments / income > 0.3) || v.getOrDefault("DEBTS", 0d) > 0 && income != null && v.get("DEBTS") > income * 6) {
            steps.add(new Step("DEBT", "بدهی‌های گرون و جریمه‌دار",
                    "قسط‌ها سهم سنگینی از درآمده. سودِ صاف کردن بدهی جریمه‌دار از تقریباً هر سرمایه‌گذاری‌ای مطمئن‌تره."));
        }
        if (goal != null && !goal.reachable()) {
            StringBuilder ways = new StringBuilder("با پس‌انداز فعلی تا " + goal.months() + " ماه دیگه به حدود "
                    + amount(goal.projected()) + " میلیون می‌رسید و هدف " + amount(goal.amount()) + " میلیونه. به‌جای ریسک بیشتر، سه راه هست: ");
            ways.append(goal.monthsNeeded() == null ? "زمان بیشتر" : "زمان بیشتر (حدود " + goal.monthsNeeded() + " ماه)")
                    .append("، پس‌انداز بیشتر (حدود ").append(amount(goal.extraMonthly())).append(" میلیون اضافه در ماه)")
                    .append("، یا هدف کوچیک‌تر. عددها به قیمت امروزه.");
            steps.add(new Step("GOAL", "هدف با زمان و پولتون جور نیست", ways.toString()));
        }
        double investable = Math.max(0, cash - reserve) + v.getOrDefault("GOLD", 0d) + v.getOrDefault("CURRENCY", 0d)
                + v.getOrDefault("STOCKS", 0d);

        int at = LEVELS.indexOf(profile);
        List<String> levels = new ArrayList<>();
        if (at > 0) {
            levels.add(LEVELS.get(at - 1));
        }
        levels.add(profile);
        if (at < LEVELS.size() - 1 && LEVELS.indexOf(capacity) > at) {
            levels.add(LEVELS.get(at + 1));
        }
        List<Scenario> scenarios = new ArrayList<>();
        for (String level : levels) {
            List<Share> shares = template(level);
            Map<String, Double> amounts = new LinkedHashMap<>();
            if (investable > 0) {
                shares.forEach(share -> amounts.put(share.id(), investable * share.middle() / 100.0));
            }
            String title = profileTitle(level) + (level.equals(profile) ? " (هم‌اندازهٔ پروفایل)" : "");
            scenarios.add(new Scenario(level, title, scenarioText(level), shares, amounts, fa(badYear(level, investable))));
        }
        // what people read gets Persian digits
        List<Step> shown = steps.stream().map(step -> new Step(step.id(), step.title(), fa(step.text()))).toList();
        return new Plan(profile, fa(note), shown, investable, scenarios);
    }

    static String fa(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c == ',' ? '٬' : c);
        }
        return out.toString();
    }

    static String capacityReason(String why) {
        if (why == null) {
            return "";
        }
        return switch (why) {
            case "monthly spending is more than income" -> "خرج ماهانه از درآمد بیشتره";
            case "less than one month of essential costs in cash" -> "کمتر از یک ماه هزینهٔ ضروری پول نقد دارید";
            case "less than three months of essential costs in cash" -> "کمتر از سه ماه هزینهٔ ضروری پول نقد دارید";
            case "instalments take more than 40% of income" -> "قسط‌ها بیشتر از ٪۴۰ درآمده";
            default -> why;
        };
    }

    static String amount(double millions) {
        return String.format(java.util.Locale.ROOT, "%,.0f", millions);
    }
}
