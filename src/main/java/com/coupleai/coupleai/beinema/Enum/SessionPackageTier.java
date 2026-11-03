package com.coupleai.coupleai.beinema.Enum;

import java.util.List;

/**
 * The plans on sale (docs: 01-pricing/pricing-business-spec-FA.md, the source of truth for every
 * amount). Each has a regular price and up to two discount steps of 100 buyers each, counted per
 * plan and on the server (PromoTierCounter). The name stays SessionPackageTier for the rows
 * already in the database; old bundles (BRONZE ...) keep working by their own name.
 */
public enum SessionPackageTier {
    TRIAL("بستهٔ آزمایشی", 2, 590_000, 30, 1, false, false,
            List.of(new Step(1, 50, 295_000, 100))),
    MAIN("بستهٔ همراه", 8, 1_990_000, 60, 2, true, false,
            List.of(new Step(1, 50, 995_000, 100), new Step(2, 30, 1_393_000, 100))),
    DIAMOND("بستهٔ سه‌ماهه", 20, 4_490_000, 90, 3, true, false,
            List.of(new Step(1, 50, 2_245_000, 100), new Step(2, 30, 3_143_000, 100))),
    MONTHLY("اشتراک ماهانه", 4, 990_000, 30, 1, false, true,
            List.of(new Step(1, 50, 495_000, 100), new Step(2, 30, 693_000, 100)));

    /** One discount step: its number, percent off, the price and how many buyers it is for. */
    public record Step(int number, int percent, long price, int capacity) {
    }

    private final String title;
    private final int sessions;
    private final long regularPrice;
    private final int validDays;
    private final int dailySessions;
    private final boolean refundable;
    private final boolean recurring;
    private final List<Step> steps;

    SessionPackageTier(String title, int sessions, long regularPrice, int validDays, int dailySessions,
                       boolean refundable, boolean recurring, List<Step> steps) {
        this.title = title;
        this.sessions = sessions;
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

    /** The regular price of one session, used for refunds. */
    public long regularPerSession() {
        return regularPrice / sessions;
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
