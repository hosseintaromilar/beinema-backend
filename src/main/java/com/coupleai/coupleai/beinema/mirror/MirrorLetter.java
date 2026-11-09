package com.coupleai.coupleai.beinema.mirror;

import java.util.List;

/**
 * The email of the nightly mirror: right to left, in the site's colours, built with inline styles
 * and plain blocks so mail programs (Gmail, Outlook, phones) show it the same way. The same words
 * go out as plain text too.
 */
public final class MirrorLetter {

    static final String PRIVACY = "این گزارش به‌صورت خودکار برای شما و نفر دیگرِ این گفتگو ساخته و فرستاده شده است "
            + "و فقط شما دو نفر به آن دسترسی دارید.";

    /** The parts of one mirror, already read from the report. */
    public record Content(String greetingName, String roomTitle, String date, String mood, String moodLabel,
                          String moodHint, String dailySentence, String whereWeAre, String whereWeWere,
                          List<String> needs, List<String> patterns, String nextStep, String roomUrl,
                          String stopUrl) {
    }

    private MirrorLetter() {
    }

    public static String subject(Content c) {
        return "آینهٔ امروز «" + c.roomTitle() + "»";
    }

    public static String html(Content c) {
        StringBuilder body = new StringBuilder();
        body.append("<div style=\"font-size:15px;line-height:2;margin:0 0 6px;\">")
                .append(c.greetingName() == null ? "سلام،" : "سلام " + esc(c.greetingName()) + "،")
                .append("</div>")
                .append("<div style=\"font-size:14px;color:#745c47;line-height:2;margin:0 0 18px;\">")
                .append("آینهٔ امروزِ گفتگوی «").append(esc(c.roomTitle())).append("» آماده است.")
                .append("</div>");

        if (notBlank(c.dailySentence())) {
            body.append("<div style=\"background:#faece3;border-right:4px solid #c9785b;border-radius:14px;")
                    .append("padding:16px 18px;margin:0 0 18px;\">")
                    .append("<div style=\"font-size:12px;color:#b86b4b;font-weight:bold;margin-bottom:6px;\">نکتهٔ امروز</div>")
                    .append("<div style=\"font-size:17px;line-height:1.9;color:#2a2420;font-weight:bold;\">")
                    .append(esc(c.dailySentence())).append("</div></div>");
        }

        body.append("<div style=\"margin:0 0 18px;\">")
                .append("<span style=\"display:inline-block;padding:4px 14px;border-radius:999px;font-size:13px;font-weight:bold;")
                .append(moodStyle(c.mood())).append("\">").append(esc(c.moodLabel())).append("</span>")
                .append("<div style=\"font-size:13px;color:#8f7a66;margin-top:6px;line-height:1.9;\">")
                .append(esc(c.moodHint())).append("</div></div>");

        section(body, "امروز کجا بودیم", c.whereWeAre());
        section(body, "جلسهٔ قبل", c.whereWeWere());
        list(body, "نیازهایی که گفته شد", c.needs());
        list(body, "الگوهایی که دیده شد", c.patterns());

        if (notBlank(c.nextStep())) {
            body.append("<div style=\"background:#4f5235;color:#fcf2e6;border-radius:14px;padding:16px 18px;margin:0 0 20px;\">")
                    .append("<div style=\"font-size:12px;opacity:.8;margin-bottom:6px;font-weight:bold;\">قدم کوچک بعدی</div>")
                    .append("<div style=\"font-size:15px;line-height:1.9;\">").append(esc(c.nextStep())).append("</div></div>");
        }

        body.append("<div style=\"text-align:center;margin:6px 0 22px;\">")
                .append("<a href=\"").append(esc(c.roomUrl())).append("\" style=\"display:inline-block;background:#b86b4b;")
                .append("color:#ffffff;text-decoration:none;font-weight:bold;font-size:14px;padding:12px 28px;border-radius:999px;\">")
                .append("دیدن آینه در بین‌ما</a></div>");

        body.append("<div style=\"background:#f2e3cd;border-radius:12px;padding:12px 14px;font-size:12.5px;color:#543c27;line-height:1.9;\">")
                .append("🔒 ").append(PRIVACY)
                .append(" این آینهٔ رابطه است، نه کارنامهٔ کسی، و جای متخصص را نمی‌گیرد.</div>");

        return "<!doctype html><html lang=\"fa\" dir=\"rtl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + esc(subject(c)) + "</title></head>"
                + "<body style=\"margin:0;padding:24px 12px;background:#f6ede1;font-family:Vazirmatn,Tahoma,Arial,sans-serif;color:#3a2f27;\">"
                + "<div style=\"max-width:580px;margin:0 auto;direction:rtl;text-align:right;\">"
                + "<div style=\"background:#4f5235;border-radius:20px 20px 0 0;padding:18px 22px;color:#fcf2e6;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\"><tr>"
                + "<td style=\"vertical-align:middle;padding-left:12px;\"><img src=\"" + MailLogo.URL + "\" width=\"48\" height=\"48\" alt=\"بین‌ما\" "
                + "style=\"display:block;width:48px;height:48px;border-radius:14px;border:0;\"></td>"
                + "<td style=\"vertical-align:middle;\"><div style=\"font-size:22px;font-weight:bold;line-height:1.3;\">بین‌ما</div>"
                + "<div style=\"font-size:14px;opacity:.85;margin-top:2px;\">آینهٔ امروز · " + esc(c.date()) + "</div></td>"
                + "</tr></table></div>"
                + "<div style=\"background:#fffaf3;border:1px solid #ecdcc5;border-top:0;border-radius:0 0 20px 20px;padding:22px;\">"
                + body
                + "</div>"
                + "<div style=\"text-align:center;font-size:12px;color:#8a7563;margin-top:14px;line-height:2;\">"
                + "<a href=\"https://beinema.ir\" style=\"color:#b86b4b;text-decoration:none;\">beinema.ir</a>"
                + " · <a href=\"" + esc(c.stopUrl()) + "\" style=\"color:#8a7563;\">دیگر آینه را ایمیل نکن</a>"
                + "<br>این ایمیل خودکار است؛ لطفاً به آن جواب ندهید."
                + "</div></div></body></html>";
    }

    public static String text(Content c) {
        StringBuilder out = new StringBuilder();
        out.append(c.greetingName() == null ? "سلام،" : "سلام " + c.greetingName() + "،").append("\n\n")
                .append("آینهٔ امروزِ گفتگوی «").append(c.roomTitle()).append("» (").append(c.date()).append(") آماده است.\n\n");
        if (notBlank(c.dailySentence())) {
            out.append("نکتهٔ امروز: ").append(c.dailySentence()).append("\n\n");
        }
        out.append("حال گفتگو: ").append(c.moodLabel()).append("\n\n");
        if (notBlank(c.whereWeAre())) {
            out.append("امروز کجا بودیم: ").append(c.whereWeAre()).append("\n\n");
        }
        if (notBlank(c.whereWeWere())) {
            out.append("جلسهٔ قبل: ").append(c.whereWeWere()).append("\n\n");
        }
        if (!c.needs().isEmpty()) {
            out.append("نیازهایی که گفته شد:\n");
            c.needs().forEach(need -> out.append("- ").append(need).append('\n'));
            out.append('\n');
        }
        if (!c.patterns().isEmpty()) {
            out.append("الگوهایی که دیده شد:\n");
            c.patterns().forEach(pattern -> out.append("- ").append(pattern).append('\n'));
            out.append('\n');
        }
        if (notBlank(c.nextStep())) {
            out.append("قدم کوچک بعدی: ").append(c.nextStep()).append("\n\n");
        }
        out.append("دیدن آینه در بین‌ما: ").append(c.roomUrl()).append("\n\n")
                .append(PRIVACY).append("\n\n")
                .append("—\nدیگر آینه را ایمیل نکن: ").append(c.stopUrl());
        return out.toString();
    }

    private static void section(StringBuilder body, String title, String text) {
        if (!notBlank(text)) {
            return;
        }
        body.append("<div style=\"margin:0 0 16px;\">")
                .append("<div style=\"font-size:12px;color:#8f7a66;font-weight:bold;margin-bottom:4px;\">").append(title).append("</div>")
                .append("<div style=\"font-size:15px;line-height:2;color:#2a2420;\">").append(esc(text)).append("</div></div>");
    }

    private static void list(StringBuilder body, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        body.append("<div style=\"margin:0 0 16px;\">")
                .append("<div style=\"font-size:12px;color:#8f7a66;font-weight:bold;margin-bottom:6px;\">").append(title).append("</div>");
        for (String item : items) {
            body.append("<div style=\"background:#f9eddc;border-radius:10px;padding:8px 12px;margin:0 0 6px;font-size:14px;line-height:1.9;\">")
                    .append("• ").append(esc(item)).append("</div>");
        }
        body.append("</div>");
    }

    private static String moodStyle(String mood) {
        return switch (mood == null ? "" : mood) {
            case "softer" -> "background:#e3f0e6;color:#2f6f47;";
            case "returned" -> "background:#f7e1dc;color:#9e3b32;";
            case "starting" -> "background:#e6ecf5;color:#3d5f86;";
            default -> "background:#f2e3cd;color:#745c47;";
        };
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    static String esc(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
