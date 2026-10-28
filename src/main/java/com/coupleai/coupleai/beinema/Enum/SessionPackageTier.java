package com.coupleai.coupleai.beinema.Enum;

/**
 * The session bundles: a number of sessions for one price, to use within a
 * month. Cheaper per session the bigger the bundle (a single session costs
 * the session price, see SessionBillingService).
 */
public enum SessionPackageTier {
    BRONZE("برنزی", 4, 300_000),
    SILVER("نقره‌ای", 10, 700_000),
    GOLD("طلایی", 24, 1_500_000);

    /** Days a bundle can be used after it is bought. */
    public static final int VALID_DAYS = 30;

    private final String title;
    private final int sessions;
    private final long price;

    SessionPackageTier(String title, int sessions, long price) {
        this.title = title;
        this.sessions = sessions;
        this.price = price;
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
}
