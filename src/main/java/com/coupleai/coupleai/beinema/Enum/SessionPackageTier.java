package com.coupleai.coupleai.beinema.Enum;

/**
 * The session bundles: a number of sessions for one price, to use within a
 * month. Cheaper per session the bigger the bundle (a single session costs
 * the session price, see SessionBillingService), and more sessions a day.
 */
public enum SessionPackageTier {
    BRONZE("برنزی", 4, 300_000, 2, null),
    SILVER("نقره‌ای", 10, 700_000, 3, null),
    GOLD("طلایی", 24, 1_500_000, 4, "دسترسی به همهٔ همراه‌ها"),
    DIAMOND("الماس", 40, 2_200_000, 6, "دسترسی زودتر از بقیه به همراه‌های تازه");

    /** Days a bundle can be used after it is bought. */
    public static final int VALID_DAYS = 30;

    /** The struck-through price is at most this many times the real one (a discount of at most 50%). */
    public static final int LIST_PRICE_FACTOR = 2;

    private final String title;
    private final int sessions;
    private final long price;
    private final int dailySessions;
    private final String perk;

    SessionPackageTier(String title, int sessions, long price, int dailySessions, String perk) {
        this.title = title;
        this.sessions = sessions;
        this.price = price;
        this.dailySessions = dailySessions;
        this.perk = perk;
    }

    public String title() {
        return title;
    }

    public int sessions() {
        return sessions;
    }

    public long price() {
        return price;
    }

    /** Sessions a conversation of its owner may hold in 24 hours while the bundle is active. */
    public int dailySessions() {
        return dailySessions;
    }

    /** What else it gives, shown on the wallet page; null for none. */
    public String perk() {
        return perk;
    }
}
