package com.coupleai.coupleai.beinema.governance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReentryCommitment {

    public static final String KIND_COMMIT = "COMMIT";
    public static final String KIND_CONSENT = "CONSENT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";

    public static final String QUESTION =
            "برای برگشتن به گفتگو، لطفاً این تعهد را بپذیر: "
                    + "به فرایند جلسه احترام می‌گذارم، به طرف مقابلم حمله شخصی نمی‌کنم، "
                    + "و به مداخله مشاور گوش می‌دهم.";

    public static final String CONSENT_QUESTION =
            "قوانین این گفتگو و چارچوب جلسه مشاوره را می‌پذیرم: "
                    + "با احترام حرف می‌زنم، به طرف مقابلم حمله شخصی نمی‌کنم، "
                    + "و به هدایت مشاور گوش می‌دهم.";

    public static final String ACCEPT_LABEL = "می‌پذیرم و آماده‌ام ادامه بدهم";
    public static final String DECLINE_LABEL = "هنوز آماده‌ام نیستم";
    public static final String CONSENT_ACCEPT_LABEL = "موافقم و آماده‌ام شروع کنم";
    public static final String CONSENT_DECLINE_LABEL = "الان موافق نیستم";

    public static final String FALLBACK_OPENING =
            "سلام. من مشاور این جلسه‌ام. گوش می‌دهم، در زمان مناسب مداخله می‌کنم، "
                    + "و اگر فضا توهین‌آمیز شد موقتاً ارسال پیام را متوقف می‌کنم تا آرام شویم. این مکث تنبیه نیست.";

    private static final Pattern COMMIT_MARKER = Pattern.compile(
            "\\[\\[COMMIT\\|USER=(\\d+)\\|STATUS=([A-Z]+)\\]\\]",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AGREE_MARKER = Pattern.compile(
            "\\[\\[AGREE\\|KIND=([A-Z]+)\\|USER=(\\d+)\\|STATUS=([A-Z]+)\\]\\]",
            Pattern.CASE_INSENSITIVE
    );

    private ReentryCommitment() {
    }

    public static String pendingContent(Long userId) {
        long id = userId == null ? 0L : userId;
        return "[[COMMIT|USER=" + id + "|STATUS=" + STATUS_PENDING + "]]\n" + QUESTION;
    }

    public static String consentMarker(Long userId) {
        long id = userId == null ? 0L : userId;
        return "[[AGREE|KIND=" + KIND_CONSENT + "|USER=" + id + "|STATUS=" + STATUS_PENDING + "]]";
    }

    public static String consentContent(Long userId) {
        return consentMarker(userId) + "\n" + CONSENT_QUESTION;
    }

    public static String kind(String content) {
        Matcher agree = AGREE_MARKER.matcher(content == null ? "" : content);
        if (agree.find()) {
            return agree.group(1).toUpperCase();
        }
        if (COMMIT_MARKER.matcher(content == null ? "" : content).find()) {
            return KIND_COMMIT;
        }
        return null;
    }

    public static Long userId(String content) {
        Matcher agree = AGREE_MARKER.matcher(content == null ? "" : content);
        if (agree.find()) {
            return parseLong(agree.group(2));
        }
        Matcher commit = COMMIT_MARKER.matcher(content == null ? "" : content);
        if (commit.find()) {
            return parseLong(commit.group(1));
        }
        return null;
    }

    public static String status(String content) {
        Matcher agree = AGREE_MARKER.matcher(content == null ? "" : content);
        if (agree.find()) {
            return agree.group(3).toUpperCase();
        }
        Matcher commit = COMMIT_MARKER.matcher(content == null ? "" : content);
        if (commit.find()) {
            return commit.group(2).toUpperCase();
        }
        return null;
    }

    public static String visible(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return AGREE_MARKER.matcher(COMMIT_MARKER.matcher(content).replaceAll("")).replaceAll("").trim();
    }

    private static Long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
