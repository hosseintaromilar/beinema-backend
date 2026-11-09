package com.coupleai.coupleai.beinema.analytics;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reading a visit: where it came from (the referring site or the campaign tag), the kind of
 * device, browser and system, whether it is a robot, and the page path in a form that groups
 * well (ids and tokens folded). Pure functions, no state.
 */
public final class Traffic {

    public static final String DIRECT = "DIRECT";
    public static final String OTHER = "OTHER";
    public static final String EMAIL = "EMAIL";

    /** A known source: its key, and the host endings or campaign names that mean it. */
    private record Known(String key, List<String> hosts, List<String> names) {
    }

    // order matters: Gemini before Google, specific before general
    private static final List<Known> KNOWN = List.of(
            new Known("CHATGPT", List.of("chatgpt.com", "chat.openai.com", "openai.com"), List.of("chatgpt", "openai")),
            new Known("GEMINI", List.of("gemini.google.com", "bard.google.com"), List.of("gemini")),
            new Known("PERPLEXITY", List.of("perplexity.ai"), List.of("perplexity")),
            new Known("GOOGLE", List.of("google."), List.of("google")),
            new Known("BING", List.of("bing.com"), List.of("bing")),
            new Known("DUCKDUCKGO", List.of("duckduckgo.com"), List.of("duckduckgo")),
            new Known("YANDEX", List.of("yandex."), List.of("yandex")),
            new Known("YAHOO", List.of("yahoo."), List.of("yahoo")),
            new Known("INSTAGRAM", List.of("instagram.com"), List.of("instagram", "ig", "insta")),
            new Known("TELEGRAM", List.of("t.me", "telegram.org", "telegram.me"), List.of("telegram", "tg")),
            new Known("WHATSAPP", List.of("wa.me", "whatsapp.com"), List.of("whatsapp", "wa")),
            new Known("TWITTER", List.of("twitter.com", "x.com", "t.co"), List.of("twitter", "x")),
            new Known("LINKEDIN", List.of("linkedin.com", "lnkd.in"), List.of("linkedin")),
            new Known("FACEBOOK", List.of("facebook.com", "fb.com", "fb.me"), List.of("facebook", "fb")),
            new Known("YOUTUBE", List.of("youtube.com", "youtu.be"), List.of("youtube")),
            new Known("APARAT", List.of("aparat.com"), List.of("aparat")),
            new Known("EITAA", List.of("eitaa.com"), List.of("eitaa")),
            new Known("RUBIKA", List.of("rubika.ir"), List.of("rubika")),
            new Known("BALE", List.of("ble.ir", "bale.ai"), List.of("bale")),
            new Known("VIRGOOL", List.of("virgool.io"), List.of("virgool"))
    );

    /** Our own hosts: a link from one of our pages to another is not a source. */
    private static final List<String> OWN = List.of("beinema.ir", "localhost", "127.0.0.1");

    private static final Pattern BOT = Pattern.compile(
            "(?i)bot\\b|bot/|crawl|spider|slurp|headless|lighthouse|pagespeed|pingdom|uptime|monitor|"
                    + "curl/|wget/|python|java/|go-http|okhttp|axios|node-fetch|postman|preview|facebookexternalhit|"
                    + "embedly|whatsapp/|telegrambot|discordbot|skypeuripreview|bingpreview|semrush|ahrefs|mj12");

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern UUID = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,}");

    public record Source(String key, String host) {
    }

    private Traffic() {
    }

    /**
     * Where the visit came from. A referring site wins; with none (apps often hide it), the
     * campaign tag names it; with neither, it is direct.
     */
    public static Source source(String referrer, String utmSource, String utmMedium) {
        String host = host(referrer);
        if (host != null && !own(host)) {
            for (Known known : KNOWN) {
                for (String ending : known.hosts()) {
                    if (matches(host, ending)) {
                        return new Source(known.key(), host);
                    }
                }
            }
            return new Source(OTHER, host);
        }
        String tag = clean(utmSource, 80);
        if (tag != null) {
            String name = tag.toLowerCase(Locale.ROOT);
            for (Known known : KNOWN) {
                if (known.names().contains(name) || known.hosts().stream().anyMatch(h -> matches(name, h))) {
                    return new Source(known.key(), null);
                }
            }
            if ("email".equalsIgnoreCase(utmMedium) || name.contains("mail") || name.contains("newsletter")) {
                return new Source(EMAIL, null);
            }
            return new Source(OTHER, name);
        }
        if ("email".equalsIgnoreCase(utmMedium)) {
            return new Source(EMAIL, null);
        }
        return new Source(DIRECT, null);
    }

    private static boolean matches(String host, String ending) {
        if (ending.endsWith(".")) {
            // google. means any google domain: google.com, google.co.ir, www.google.de
            return host.startsWith(ending) || host.contains("." + ending);
        }
        return host.equals(ending) || host.endsWith("." + ending);
    }

    private static boolean own(String host) {
        return OWN.stream().anyMatch(o -> host.equals(o) || host.endsWith("." + o));
    }

    /** The host of a referring address, lower case, without www; null when there is none. */
    static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            host = host.startsWith("www.") ? host.substring(4) : host;
            return host.startsWith("m.") || host.startsWith("l.") || host.startsWith("lm.") ? host.substring(host.indexOf('.') + 1) : host;
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }

    public static boolean robot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find();
    }

    public static String device(String ua) {
        if (ua == null) {
            return "DESKTOP";
        }
        if (ua.contains("iPad") || ua.contains("Tablet") || (ua.contains("Android") && !ua.contains("Mobile"))) {
            return "TABLET";
        }
        if (ua.contains("Mobi") || ua.contains("iPhone") || ua.contains("iPod") || ua.contains("Android")) {
            return "MOBILE";
        }
        return "DESKTOP";
    }

    public static String browser(String ua) {
        if (ua == null) {
            return OTHER;
        }
        if (ua.contains("Instagram")) return "Instagram";
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("SamsungBrowser")) return "Samsung";
        if (ua.contains("YaBrowser")) return "Yandex";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        return OTHER;
    }

    public static String system(String ua) {
        if (ua == null) {
            return OTHER;
        }
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) return "iOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Linux")) return "Linux";
        return OTHER;
    }

    /**
     * A page path that groups well: no query or fragment, decoded (Persian slugs read as words),
     * numbers and long tokens folded so /chat/52 and /chat/53 count as one page.
     */
    public static String path(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/";
        }
        String path = raw.trim();
        int cut = indexOfAny(path, '?', '#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        try {
            path = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException broken) {
            // keep it as it came
        }
        StringBuilder out = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            String folded = DIGITS.matcher(segment).matches() || UUID.matcher(segment).matches() ? ":id"
                    : TOKEN.matcher(segment).matches() && segment.chars().anyMatch(Character::isDigit) ? ":token"
                    : segment;
            out.append('/').append(folded);
        }
        String result = out.length() == 0 ? "/" : out.toString();
        return result.length() > 200 ? result.substring(0, 200) : result;
    }

    private static int indexOfAny(String text, char a, char b) {
        int i = text.indexOf(a);
        int j = text.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
    }

    /** Trimmed, at most max characters, null when empty. */
    public static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
