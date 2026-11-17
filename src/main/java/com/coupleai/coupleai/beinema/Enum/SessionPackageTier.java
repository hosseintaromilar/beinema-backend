package com.coupleai.coupleai.beinema.Enum;

import java.util.List;

/**
 * The plans. On sale (from Azar 1405): a single session and bundles of 4, 10 and 20 sessions, a few of
 * them free in each bundle, so the bigger the bundle the less one session costs; valid for a year; the
 * bigger the bundle the more sessions a day, at most four, so there is time between sessions to think
 * about them. The older plans (TRIAL ... MONTHLY) are no longer sold but keep working, with their own
 * terms, for the bundles already bought; old bundles (BRONZE ...) keep working by their own name.
 * A plan may have discount steps of 100 buyers each, counted on the server (PromoTierCounter).
 */
public enum SessionPackageTier {
    SINGLE("تک جلسه", 1, 0, 349_000, 365, 1, false, false, true, List.of()),
    // launch discount: 50% for the first 100 buyers of each bundle, 30% for the next 100
    PACK4("بستهٔ ۴ جلسه‌ای", 4, 1, 999_000, 365, 2, true, false, true,
            List.of(new Step(1, 50, 499_000, 100), new Step(2, 30, 699_000, 100))),
    PACK10("بستهٔ ۱۰ جلسه‌ای", 10, 2, 2_290_000, 365, 3, true, false, true,
            List.of(new Step(1, 50, 1_145_000, 100), new Step(2, 30, 1_603_000, 100))),
    PACK20("بستهٔ ۲۰ جلسه‌ای", 20, 5, 3_990_000, 365, 4, true, false, true,
            List.of(new Step(1, 50, 1_995_000, 100), new Step(2, 30, 2_793_000, 100))),

    TRIAL("بستهٔ آزمایشی", 2, 590_000, 30, 1, false, false,
            List.of(new Step(1, 50, 295_000, 100))),
    MAIN("بستهٔ همراه", 8, 1_990_000, 60, 2, true, false,
            List.of(new Step(1, 50, 995_000, 100), new Step(2, 30, 1_393_000, 100))),
    DIAMOND("بستهٔ سه‌ماهه", 20, 4_490_000, 90, 3, true, false,
            List.of(new Step(1, 50, 2_245_000, 100), new Step(2, 30, 3_143_000, 100))),
    MONTHLY("اشتراک ماهانه", 4, 990_000, 30, 1, false, true,
            List.of(new Step(1, 50, 495_000, 100), new Step(2, 30, 693_000, 100)));

    /** The price of one session on its own: used sessions of a cancelled bundle are counted at it. */
    public static final long SINGLE_PRICE = 349_000;

    /** One discount step: its number, percent off, the price and how many buyers it is for. */
    public record Step(int number, int percent, long price, int capacity) {
    }

    private final String title;
    private final int sessions;
    /** Of the sessions, how many are free (shown as «۳ + ۱ رایگان»). */
    private final int bonus;
    private final boolean onSale;
    private final long regularPrice;
    private final int validDays;
    private final int dailySessions;
    private final boolean refundable;
    private final boolean recurring;
    private final List<Step> steps;

    /** An older plan: not sold any more, no free sessions. */
    SessionPackageTier(String title, int sessions, long regularPrice, int validDays, int dailySessions,
                       boolean refundable, boolean recurring, List<Step> steps) {
        this(title, sessions, 0, regularPrice, validDays, dailySessions, refundable, recurring, false, steps);
    }

    SessionPackageTier(String title, int sessions, int bonus, long regularPrice, int validDays, int dailySessions,
                       boolean refundable, boolean recurring, boolean onSale, List<Step> steps) {
        this.title = title;
        this.sessions = sessions;
        this.bonus = bonus;
        this.onSale = onSale;
        this.regularPrice = regularPrice;
        this.validDays = validDays;
        this.dailySessions = dailySessions;
        this.refundable = refundable;
        this.recurring = recurring;
        this.steps = steps;
    }

    public String title() {
        return title;
    }

    public int sessions() {
        return sessions;
    }

    /** Of the sessions, the free ones. */
    public int bonus() {
        return bonus;
    }

    /** Sold now; the older plans only keep working for the bundles already bought. */
    public boolean onSale() {
        return onSale;
    }

    /** The price without a discount. */
    public long regularPrice() {
        return regularPrice;
    }

    /** Days a bundle can be used after it is bought (a month for the subscription). */
    public int validDays() {
        return validDays;
    }

    /** Sessions a conversation of its owner may hold in a day while the bundle is active. */
    public int dailySessions() {
        return dailySessions;
    }

    /** Can be cancelled with the unused sessions paid back (used ones at the regular price). */
    public boolean refundable() {
        return refundable;
    }

    /** Renews every month until cancelled. */
    public boolean recurring() {
        return recurring;
    }

    public List<Step> steps() {
        return steps;
    }

    /**
     * What one used session of a cancelled bundle costs: the price of a single session for the plans on
     * sale (a bundle's low price is for the whole bundle), the bundle's own per-session price for the older ones.
     */
    public long regularPerSession() {
        return onSale ? SINGLE_PRICE : regularPrice / sessions;
    }

    /** The plan of a stored name, or null for an old or unknown one. */
    public static SessionPackageTier of(String name) {
        try {
            return name == null ? null : valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
