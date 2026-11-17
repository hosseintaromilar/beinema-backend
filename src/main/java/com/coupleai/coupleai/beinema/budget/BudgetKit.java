package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.market.MarketDataService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The household budget book's fixed parts: the categories of income and spending (with the words
 * people use for them), and the Iranian month an entry belongs to. Months are Jalali: «آبان ۱۴۰۵».
 */
public final class BudgetKit {

    public static final String INCOME = "INCOME";
    public static final String EXPENSE = "EXPENSE";

    /** One category: its id, its Persian name, income or spending. */
    public record Category(String id, String label, String kind) {
    }

    public static final List<Category> CATEGORIES = List.of(
            new Category("SALARY", "حقوق و دستمزد", INCOME),
            new Category("BUSINESS", "کسب‌وکار و کار آزاد", INCOME),
            new Category("RENT_IN", "اجاره و درآمد دارایی", INCOME),
            new Category("OTHER_IN", "درآمدهای دیگر", INCOME),
            new Category("HOUSING", "مسکن و اجاره", EXPENSE),
            new Category("FOOD", "خوراک و خرید خانه", EXPENSE),
            new Category("BILLS", "قبض‌ها و شارژ", EXPENSE),
            new Category("TRANSPORT", "رفت‌وآمد و خودرو", EXPENSE),
            new Category("INSTALMENTS", "قسط و بدهی", EXPENSE),
            new Category("HEALTH", "درمان و بیمه", EXPENSE),
            new Category("CHILDREN", "بچه‌ها و آموزش", EXPENSE),
            new Category("FAMILY", "کمک به خانواده‌ها", EXPENSE),
            new Category("CLOTHES", "پوشاک و وسایل شخصی", EXPENSE),
            new Category("FUN", "تفریح، رستوران و سفر", EXPENSE),
            new Category("GIFTS", "هدیه و مناسبت", EXPENSE),
            new Category("SAVING", "پس‌انداز و سرمایه‌گذاری", EXPENSE),
            new Category("OTHER", "خرج‌های دیگر", EXPENSE));

    /** Saving is put aside, not spent: it is shown apart from the spending. */
    public static final String SAVING = "SAVING";

    static final String[] MONTHS = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};

    private BudgetKit() {
    }

    public static Category category(String id) {
        return CATEGORIES.stream().filter(category -> category.id().equals(id)).findFirst().orElse(null);
    }

    public static Map<String, String> labels() {
        Map<String, String> out = new LinkedHashMap<>();
        CATEGORIES.forEach(category -> out.put(category.id(), category.label()));
        return out;
    }

    /** The Jalali month of a day: «1405/08». */
    public static String month(LocalDate day) {
        return MarketDataService.jalali(day).substring(0, 7);
    }

    /** «آبان ۱۴۰۵» from «1405/08». */
    public static String monthName(String month) {
        String[] parts = month.split("/");
        int index = Integer.parseInt(parts[1]) - 1;
        return MONTHS[Math.max(0, Math.min(11, index))] + " " + digits(parts[0]);
    }

    /** The month before or after: «1405/08» and -1 give «1405/07». */
    public static String shift(String month, int by) {
        String[] parts = month.split("/");
        int total = Integer.parseInt(parts[0]) * 12 + Integer.parseInt(parts[1]) - 1 + by;
        return String.format("%d/%02d", total / 12, total % 12 + 1);
    }

    public static boolean valid(String month) {
        return month != null && month.matches("\\d{4}/(0[1-9]|1[0-2])");
    }

    static String digits(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }
}
