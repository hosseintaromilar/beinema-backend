package com.coupleai.coupleai.beinema.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The counselor brain (Metis) may write one reply as several short messages and
 * may add a sticker or ask that the first message be sent as a reply to the
 * message that triggered the turn. It does so with inline markers:
 *
 * <pre>
 *   [[BREAK|PAUSE=2]]        start a new message after a short pause
 *   [[STICKER|NAME=THANKS]]  send that sticker as its own message
 *   [[REPLY]]                reply to the message that triggered this turn
 * </pre>
 *
 * This class only cuts the text and reads the markers. It never decides
 * whether or where to break, and never picks a sticker.
 */
public final class ReplySegments {

    public static final int MAX_SEGMENTS = 4;
    public static final int MAX_PAUSE_SECONDS = 4;

    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[(BREAK|STICKER|REPLY)(?:\\|([^\\]]*))?\\]\\]",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAUSE = Pattern.compile("PAUSE\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("NAME\\s*=\\s*([A-Za-z_]+)", Pattern.CASE_INSENSITIVE);

    /** One delivered message: text, or a sticker when {@code stickerToken} is set. */
    public record Segment(String text, int pauseSeconds, String stickerToken) {
        public Segment(String text, int pauseSeconds) {
            this(text, pauseSeconds, null);
        }

        public boolean isSticker() {
            return stickerToken != null;
        }
    }

    public record Parsed(List<Segment> segments, boolean replyToTrigger) {
    }

    private ReplySegments() {
    }

    public static List<Segment> split(String reply) {
        return parse(reply).segments();
    }

    public static Parsed parse(String reply) {
        List<Segment> segments = new ArrayList<>();
        if (reply == null || reply.isBlank()) {
            return new Parsed(segments, false);
        }
        boolean replyToTrigger = false;
        boolean stickerUsed = false;
        Matcher matcher = MARKER.matcher(reply);
        int cursor = 0;
        int pendingPause = 0;
        while (matcher.find()) {
            String kind = matcher.group(1).toUpperCase();
            String text = reply.substring(cursor, matcher.start());
            cursor = matcher.end();
            switch (kind) {
                case "BREAK" -> {
                    addText(segments, text, pendingPause);
                    pendingPause = readPause(matcher.group(2));
                }
                case "STICKER" -> {
                    if (addText(segments, text, pendingPause)) {
                        pendingPause = 0;
                    }
                    String token = CounselorSignal.normalize(readName(matcher.group(2)));
                    // At most one sticker per reply, and only known ones.
                    if (token != null && !stickerUsed) {
                        segments.add(new Segment("", pendingPause, token));
                        stickerUsed = true;
                        pendingPause = 0;
                    }
                }
                default -> {
                    // REPLY: stays inside the running text, only sets the flag.
                    replyToTrigger = true;
                    addText(segments, text, pendingPause);
                    pendingPause = 0;
                }
            }
        }
        addText(segments, reply.substring(cursor), pendingPause);
        return new Parsed(mergeOverflow(segments), replyToTrigger);
    }

    /** All text segments joined into a single readable text (stickers dropped). */
    public static String plain(String reply) {
        if (reply == null || reply.isBlank()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (Segment segment : split(reply)) {
            if (segment.isSticker()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append("\n\n");
            }
            joined.append(segment.text());
        }
        return joined.toString();
    }

    private static boolean addText(List<Segment> segments, String raw, int pauseSeconds) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return false;
        }
        segments.add(new Segment(text, pauseSeconds));
        return true;
    }

    private static int readPause(String options) {
        if (options == null) {
            return 0;
        }
        Matcher matcher = PAUSE.matcher(options);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Math.min(MAX_PAUSE_SECONDS, Math.max(0, Integer.parseInt(matcher.group(1))));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String readName(String options) {
        if (options == null) {
            return null;
        }
        Matcher matcher = NAME.matcher(options);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<Segment> mergeOverflow(List<Segment> segments) {
        if (segments.size() <= MAX_SEGMENTS) {
            return segments;
        }
        List<Segment> merged = new ArrayList<>(segments.subList(0, MAX_SEGMENTS - 1));
        StringBuilder tail = new StringBuilder();
        int pause = segments.get(MAX_SEGMENTS - 1).pauseSeconds();
        for (Segment segment : segments.subList(MAX_SEGMENTS - 1, segments.size())) {
            if (segment.isSticker()) {
                continue;
            }
            if (tail.length() > 0) {
                tail.append("\n\n");
            }
            tail.append(segment.text());
        }
        if (tail.length() > 0) {
            merged.add(new Segment(tail.toString(), pause));
        }
        return merged;
    }
}
