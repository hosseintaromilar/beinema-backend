package com.coupleai.coupleai.beinema.governance;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The counselor's own sticker set. The brain (Metis) decides when a sticker
 * says it better than words and which one; this class only knows the catalog,
 * reads the token and never decides anything therapeutic.
 *
 * A token can reach us in two ways: as REACT=/STICKER= inside the director
 * packet of a silent turn, or as an inline [[STICKER|NAME=...]] marker inside a
 * spoken reply (handled by {@link ReplySegments}).
 */
public record CounselorSignal(String reactionToken, String stickerToken) {

    /** token -> fallback emoji (used only when the artwork is unavailable). */
    private static final Map<String, String> EMOJI = new LinkedHashMap<>();

    /**
     * Stickers that exist in the artwork but that a specialist would not send:
     * they take sides (AGREE, RIGHT), minimize or promise (NOWORRY, BETTER,
     * ALLOK, ALWAYSWITH), judge (EMOTIONAL), command (CALMDOWN) or are too
     * casual (EXCELLENT). The brain never sees them and the backend ignores
     * them if they still arrive. Remove a name from this set to enable it.
     */
    private static final Set<String> HELD_BACK = Set.of(
            "AGREE", "RIGHT", "NOWORRY", "EMOTIONAL", "CALMDOWN",
            "BETTER", "EXCELLENT", "ALWAYSWITH", "ALLOK"
    );

    static {
        EMOJI.put("LISTEN", "\uD83D\uDC42");        // دارم گوش می‌دم
        EMOJI.put("THINKING", "\uD83E\uDD14");      // در حال فکر کردنم
        EMOJI.put("CALM", "\u2615");                 // آرام باش، دارم فکر می‌کنم
        EMOJI.put("AGREE", "\uD83D\uDC4D");         // موافقم
        EMOJI.put("ACK", "\uD83D\uDC4C");           // متوجه‌ات هستم
        EMOJI.put("REFLECT", "\uD83D\uDCAD");       // به حرف‌هات فکر می‌کنم
        EMOJI.put("IMPORTANT", "\u2764\uFE0F");     // حرف‌هات برام مهمه
        EMOJI.put("QUESTION", "\u2753");             // یه سوال دارم
        EMOJI.put("GOODTHOUGHT", "\uD83C\uDF3F");   // فکر خوبی داری
        EMOJI.put("NOWORRY", "\uD83D\uDE0C");       // نگران نباش
        EMOJI.put("NICE", "\u2728");                 // چه خوب!
        EMOJI.put("PATIENT", "\u231B");              // صبورم
        EMOJI.put("THANKS", "\uD83D\uDE4F");        // ممنون
        EMOJI.put("THANKSALOT", "\uD83D\uDE4F");    // خیلی ممنونم
        EMOJI.put("INTERESTING", "\u2757");          // جالب بود
        EMOJI.put("RIGHT", "\uD83D\uDC4C");         // درست می‌گی
        EMOJI.put("GOODIDEA", "\uD83D\uDCA1");      // ایده خوبی است
        EMOJI.put("SORRY", "\uD83D\uDE14");         // متاسفم
        EMOJI.put("NOTING", "\uD83D\uDCDD");        // یادداشت می‌کنم
        EMOJI.put("EMOTIONAL", "\uD83D\uDE33");     // خیلی احساساتی شدی
        EMOJI.put("CALMDOWN", "\uD83D\uDE0C");      // آرام باش
        EMOJI.put("BRAVO", "\uD83D\uDC4F");         // آفرین!
        EMOJI.put("NOTUNDERSTOOD", "\uD83C\uDF00"); // متوجه نشدم
        EMOJI.put("HERE", "\uD83D\uDC99");          // اینجا هستم
        EMOJI.put("BESIDE", "\uD83E\uDEC2");        // کنارت هستم
        EMOJI.put("CONTINUE", "\u27A1\uFE0F");      // بریم ادامه بدیم
        EMOJI.put("SORRYUPSET", "\uD83D\uDE14");    // متاسفم که ناراحت شدی
        EMOJI.put("BETTER", "\uD83C\uDF31");        // همه چیز بهتر می‌شه
        EMOJI.put("BYE", "\uD83D\uDC4B");           // تا بعد...
        EMOJI.put("EXCELLENT", "\uD83D\uDE0E");     // عالیه!
        EMOJI.put("REVIEWING", "\uD83D\uDCBB");     // دارم بررسی می‌کنم
        EMOJI.put("WAITFORME", "\u231B");            // منتظر بمون
        EMOJI.put("ALWAYSWITH", "\uD83D\uDC9A");    // همیشه در کنارتم
        EMOJI.put("ALLOK", "\uD83D\uDC4C");         // همه چی خوبه
    }

    private static final Pattern STICKER_MARKER = Pattern.compile(
            "\\[\\[STICKER\\|NAME=([A-Za-z_]+)\\]\\]"
    );

    public static CounselorSignal none() {
        return new CounselorSignal(null, null);
    }

    public boolean present() {
        return reactionToken != null || stickerToken != null;
    }

    /** Reads REACT= or STICKER= from a director packet that came back as the reply. */
    public static CounselorSignal read(String rawReply) {
        if (rawReply == null) {
            return none();
        }
        String trimmed = rawReply.trim();
        if (!trimmed.startsWith("ACTION=") || !trimmed.contains("|")) {
            return none();
        }
        int newline = trimmed.indexOf('\n');
        String firstLine = newline < 0 ? trimmed : trimmed.substring(0, newline);
        Map<String, String> fields = GovernancePacketParser.parsePipePacket(firstLine);
        String reaction = normalize(fields.get("REACT"));
        String sticker = reaction == null ? normalize(fields.get("STICKER")) : null;
        return new CounselorSignal(reaction, sticker);
    }

    /**
     * Upper-cases a token and returns it only if the counselor may send it:
     * it must be in the catalog and must not be held back.
     */
    public static String normalize(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        return EMOJI.containsKey(normalized) && !HELD_BACK.contains(normalized) ? normalized : null;
    }

    public static String emojiFor(String token) {
        return token == null ? null : EMOJI.get(token.trim().toUpperCase(Locale.ROOT));
    }

    public static String stickerContent(String token) {
        return "[[STICKER|NAME=" + token + "]]" + emojiFor(token);
    }

    public static String stickerName(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = STICKER_MARKER.matcher(content);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    public static boolean isSticker(String content) {
        return content != null && STICKER_MARKER.matcher(content).find();
    }

    public static String stripSticker(String content) {
        if (content == null) {
            return "";
        }
        return STICKER_MARKER.matcher(content).replaceAll("").trim();
    }
}
