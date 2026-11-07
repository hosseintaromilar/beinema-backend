package com.coupleai.coupleai.beinema.governance;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

    /**
     * The 25 stickers. Every companion has all of them, drawn from its own picture
     * (frontend/public/stickers/agents/{set}); the room's companion decides whose face shows.
     * token -> fallback emoji (shown only where the artwork is unavailable).
     */
    private static final Map<String, String> EMOJI = new LinkedHashMap<>();

    static {
        EMOJI.put("LISTEN", "👂");         // دارم گوش می‌دم
        EMOJI.put("PATIENT", "⌛");        // صبورم
        EMOJI.put("HERE", "💙");           // اینجا هستم
        EMOJI.put("BESIDE", "🫂");         // کنارت هستم
        EMOJI.put("NOTING", "📝");         // یادداشت می‌کنم
        EMOJI.put("REVIEWING", "🔍");      // دارم بررسی می‌کنم
        EMOJI.put("THINKING", "🤔");       // در حال فکر کردنم
        EMOJI.put("CALM", "🌬");            // آروم باشیم
        EMOJI.put("REFLECT", "💭");        // به حرف‌هات فکر می‌کنم
        EMOJI.put("WAITFORME", "✋");      // یه لحظه صبر کنید
        EMOJI.put("ACK", "🫶");            // درکت می‌کنم
        EMOJI.put("IMPORTANT", "❤️");      // حرف‌هات برام مهمه
        EMOJI.put("QUESTION", "❓");       // یه سؤال دارم
        EMOJI.put("NOTUNDERSTOOD", "🌀");  // متوجه نشدم
        EMOJI.put("SORRY", "😔");          // متأسفم
        EMOJI.put("SORRYUPSET", "😔");     // متأسفم که ناراحت شدی
        EMOJI.put("THANKS", "🙏");         // ممنون
        EMOJI.put("THANKSALOT", "🙏");     // خیلی ممنونم
        EMOJI.put("INTERESTING", "❗");    // جالب بود
        EMOJI.put("GOODTHOUGHT", "🌿");    // فکر خوبی داری
        EMOJI.put("GOODIDEA", "💡");       // ایده خوبیه
        EMOJI.put("NICE", "✨");           // چه خوب!
        EMOJI.put("BRAVO", "👏");          // آفرین!
        EMOJI.put("CONTINUE", "➡️");       // بریم ادامه بدیم
        EMOJI.put("BYE", "👋");            // تا بعد...
    }

    /** Session moments the platform marks itself, in the companion's name: someone waits, the session ends. */
    public static final String WAITING_PARTNER = "WAITFORME";
    public static final String WRAP_UP = "BYE";

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
     * Upper-cases a token and returns it only if it is one of the 25.
     */
    public static String normalize(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        return EMOJI.containsKey(normalized) ? normalized : null;
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
