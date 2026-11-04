package com.coupleai.coupleai.beinema.mail;

/**
 * The page version of an email: the same words, right to left, in the site's colours, with the
 * name at the top and the site (and a way to stop group emails, when given) at the bottom. Mail
 * programs that show pages read this; the others read the plain text next to it.
 */
public final class MailLetter {

    private MailLetter() {
    }

    /** text: paragraphs split by an empty line; unsubscribeUrl: shown for group emails, else null. */
    public static String html(String text, String unsubscribeUrl) {
        StringBuilder body = new StringBuilder();
        for (String paragraph : String.valueOf(text == null ? "" : text).replace("\r", "").split("\n{2,}")) {
            if (!paragraph.isBlank()) {
                body.append("<p style=\"margin:0 0 14px;line-height:2;\">")
                        .append(escape(paragraph.trim()).replace("\n", "<br>"))
                        .append("</p>");
            }
        }
        String footer = unsubscribeUrl == null ? ""
                : " · <a href=\"" + escape(unsubscribeUrl) + "\" style=\"color:#8a7563;\">دیگر ایمیل گروهی نفرست</a>";
        return "<!doctype html><html lang=\"fa\" dir=\"rtl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:24px 12px;background:#f6ede1;font-family:Tahoma,Vazirmatn,Arial,sans-serif;color:#3a2f27;\">"
                + "<div style=\"max-width:560px;margin:0 auto;background:#fffaf3;border:1px solid #ecdcc5;border-radius:18px;padding:24px 22px;direction:rtl;text-align:right;\">"
                + "<div style=\"font-size:22px;font-weight:bold;color:#4f5235;margin-bottom:18px;\">بین‌ما</div>"
                + "<div style=\"font-size:15px;\">" + body + "</div>"
                + "<div style=\"margin-top:18px;padding-top:12px;border-top:1px dashed #e2d2bc;font-size:12px;color:#8a7563;\">"
                + "<a href=\"https://beinema.ir\" style=\"color:#b86b4b;text-decoration:none;\">beinema.ir</a>" + footer
                + "</div></div></body></html>";
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
