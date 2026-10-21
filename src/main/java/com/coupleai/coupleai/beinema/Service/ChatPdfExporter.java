package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.AttachmentResponse;
import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
import com.coupleai.coupleai.beinema.governance.ReentryCommitment;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.ULocale;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Renders a chat as a right-to-left Persian PDF: a title block and then every
 * message in order with its sender and (Jalali) date.
 *
 * Plain class on purpose: it only turns data into bytes and can be tested
 * without the rest of the application.
 */
public class ChatPdfExporter {

    private static final String FONT_REGULAR = "/fonts/Vazirmatn-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/Vazirmatn-Bold.ttf";

    private final String regularFont;
    private final String boldFont;

    public ChatPdfExporter() {
        this(FONT_REGULAR, FONT_BOLD);
    }

    /** A classpath resource or a file path; lets tests compare fonts. */
    ChatPdfExporter(String regularFont, String boldFont) {
        this.regularFont = regularFont;
        this.boldFont = boldFont;
    }

    private final ULocale persian = new ULocale("fa_IR@calendar=persian");

    public record Header(
            String title,
            List<String> members,
            String agentName,
            String exportedBy,
            int sessionCount
    ) {
    }

    public byte[] render(Header header, List<MessageResponse> messages, Long viewerId) throws IOException {
        String html = html(header, messages, viewerId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> resource(regularFont), "Vazirmatn", 400,
                    BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> resource(boldFont), "Vazirmatn", 700,
                    BaseRendererBuilder.FontStyle.NORMAL, true);
            // Arabic-script shaping and right-to-left ordering
            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
            builder.defaultTextDirection(BaseRendererBuilder.TextDirection.RTL);
            builder.withHtmlContent(PersianShaper.shape(html), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = ChatPdfExporter.class.getResourceAsStream(path);
        if (stream == null) {
            java.io.File file = new java.io.File(path);
            if (file.isFile()) {
                try {
                    return new java.io.FileInputStream(file);
                } catch (IOException exception) {
                    throw new IllegalStateException("Font not readable: " + path, exception);
                }
            }
            throw new IllegalStateException("Font not found: " + path);
        }
        return stream;
    }

    // ---------------------------------------------------------------- html

    String html(Header header, List<MessageResponse> messages, Long viewerId) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" ")
                .append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"fa\" dir=\"rtl\"><head>")
                .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />")
                .append("<title>").append(esc(header.title())).append("</title>")
                .append("<style>").append(css()).append("</style></head><body>");

        html.append("<div class=\"top\">")
                .append("<div class=\"brand\">بین‌ما</div>")
                .append("<h1>").append(esc(header.title())).append("</h1>")
                .append("<div class=\"meta\">");
        if (header.members() != null && !header.members().isEmpty()) {
            row(html, "اعضای گفتگو", String.join("، ", header.members()));
        }
        if (header.agentName() != null && !header.agentName().isBlank()) {
            row(html, "مشاور", header.agentName());
        }
        row(html, "تاریخ دریافت", format(LocalDateTime.now()));
        if (header.exportedBy() != null && !header.exportedBy().isBlank()) {
            row(html, "دریافت‌کننده", header.exportedBy());
        }
        row(html, "تعداد پیام‌ها", digits(String.valueOf(visibleCount(messages))));
        html.append("</div></div>");

        String lastDay = "";
        for (MessageResponse message : messages) {
            String text = visibleText(message);
            boolean sticker = Boolean.TRUE.equals(message.getSticker());
            boolean hasFiles = message.getAttachments() != null && !message.getAttachments().isEmpty();
            if (text.isBlank() && !sticker && !hasFiles) {
                continue;
            }
            if (text.startsWith("🔧")) {
                continue; // temporary debug lines are not part of the conversation
            }

            String stamp = message.getCreatedAt() == null ? "" : format(message.getCreatedAt());
            String day = stamp.length() >= 10 ? stamp.substring(0, 10) : stamp;
            if (!day.equals(lastDay) && !day.isBlank()) {
                html.append("<div class=\"day\">").append(esc(day)).append("</div>");
                lastDay = day;
            }

            if (message.getSenderType() == MessageSenderType.SYSTEM) {
                html.append("<div class=\"system\">").append(multiline(text)).append("</div>");
                continue;
            }

            boolean agent = message.getSenderType() == MessageSenderType.AGENT;
            boolean mine = !agent && viewerId != null && viewerId.equals(message.getSenderId());
            String kind = agent ? "agent" : mine ? "me" : "partner";
            String who = agent
                    ? firstNonBlank(message.getDisplayName(), message.getSenderName(), "مشاور")
                    : mine ? "شما" : firstNonBlank(message.getDisplayName(), message.getSenderName(), "کاربر");
            String time = stamp.length() > 10 ? stamp.substring(11) : stamp;

            html.append("<div class=\"msg ").append(kind).append("\">")
                    .append("<div class=\"who\"><b>").append(esc(who)).append("</b>")
                    .append("<span class=\"time\"> · ").append(esc(time)).append("</span></div>");
            if (message.getReplyExcerpt() != null && !message.getReplyExcerpt().isBlank()) {
                String to = message.getReplyToSender() == null ? "" : message.getReplyToSender() + ": ";
                html.append("<div class=\"quote\">").append(esc(to + message.getReplyExcerpt())).append("</div>");
            }
            if (sticker) {
                html.append("<div class=\"note\">(استیکر مشاور)</div>");
            } else if (!text.isBlank()) {
                if (message.getAnswerToQuestionId() != null) {
                    html.append("<div class=\"note\">پاسخ به سؤال مشاور:</div>");
                }
                html.append("<div class=\"text\">").append(multiline(text)).append("</div>");
            }
            if (message.getQuestion() != null && message.getQuestion().getOptions() != null) {
                String chosen = message.getQuestion().getSelectedOptionId();
                for (var option : message.getQuestion().getOptions()) {
                    boolean picked = option.getId().equals(chosen);
                    html.append("<div class=\"note\">• ").append(esc(option.getLabel()))
                            .append(picked ? " (انتخاب شد)" : "").append("</div>");
                }
            }
            if (hasFiles) {
                for (AttachmentResponse file : message.getAttachments()) {
                    html.append("<div class=\"note\">پیوست: ").append(esc(file.getName())).append("</div>");
                }
            }
            html.append("</div>");
        }

        html.append("<div class=\"foot\">این فایل رونوشتی از گفتگوی شما در بین‌ماست و جایگزین پرونده‌ی درمانی نیست.</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private static void row(StringBuilder html, String label, String value) {
        html.append("<div><span class=\"k\">").append(esc(label)).append(": </span>")
                .append(esc(value)).append("</div>");
    }

    private static String css() {
        return "@page { size: A4; margin: 18mm 16mm 20mm 16mm; "
                + "@bottom-center { content: counter(page); font-family: 'Vazirmatn'; font-size: 9pt; color: #8f7a66; } }"
                + "html { direction: rtl; }"
                + "body { font-family: 'Vazirmatn'; direction: rtl; text-align: right; font-size: 11pt; "
                + "line-height: 1.9; color: #2a2420; }"
                + ".top { border-bottom: 1.5pt solid #dac7ab; padding-bottom: 10pt; margin-bottom: 14pt; }"
                + ".brand { font-weight: 700; font-size: 13pt; color: #4f5235; }"
                + "h1 { font-size: 20pt; margin: 4pt 0 8pt 0; color: #543c27; }"
                + ".meta { font-size: 10pt; color: #745c47; text-align: right; }"
                + ".meta div { margin: 1pt 0; }"
                + ".meta .k { color: #8f7a66; }"
                + ".day { text-align: right; color: #8f7a66; font-size: 9pt; margin: 12pt 0 8pt 0; }"
                + ".msg { margin: 0 0 8pt 0; padding: 6pt 10pt; border-right: 3pt solid #dac7ab; "
                + "background: #fffaf3; page-break-inside: avoid; }"
                + ".msg.agent { border-right-color: #6a6547; background: #f1f2e3; }"
                + ".msg.me { border-right-color: #4f5235; background: #f9eddc; }"
                + ".msg.partner { border-right-color: #c9785b; background: #faece3; }"
                + ".who { font-size: 9.5pt; color: #543c27; }"
                + ".time { color: #8f7a66; font-size: 8.5pt; }"
                + ".quote { font-size: 9pt; color: #8f7a66; border-right: 2pt solid #dac7ab; padding-right: 6pt; margin: 2pt 0; }"
                + ".text { margin-top: 2pt; }"
                + ".note { color: #8f7a66; font-size: 9.5pt; margin-top: 2pt; }"
                + ".system { text-align: right; color: #8f7a66; font-size: 9pt; margin: 6pt 0; }"
                + ".foot { margin-top: 18pt; padding-top: 8pt; border-top: 1pt solid #ecdcc5; "
                + "color: #8f7a66; font-size: 8.5pt; text-align: right; }";
    }

    // ---------------------------------------------------------------- text

    /** The words a person would have seen in the chat, without any control markers. */
    static String visibleText(MessageResponse message) {
        String raw = message.getContent() == null ? "" : message.getContent();
        String cleaned = new GovernancePacketParser().strip(ReentryCommitment.visible(
                com.coupleai.coupleai.beinema.governance.QuestionMarkers.strip(raw)));
        return cleaned == null ? "" : cleaned.trim();
    }

    private int visibleCount(List<MessageResponse> messages) {
        int count = 0;
        for (MessageResponse message : messages) {
            String text = visibleText(message);
            boolean sticker = Boolean.TRUE.equals(message.getSticker());
            boolean hasFiles = message.getAttachments() != null && !message.getAttachments().isEmpty();
            if ((!text.isBlank() || sticker || hasFiles) && !text.startsWith("🔧")
                    && message.getSenderType() != MessageSenderType.SYSTEM) {
                count++;
            }
        }
        return count;
    }

    private String format(LocalDateTime time) {
        Date date = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        return new SimpleDateFormat("yyyy/MM/dd HH:mm", persian).format(date);
    }

    private String digits(String latin) {
        StringBuilder out = new StringBuilder();
        for (char c : latin.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String multiline(String text) {
        return esc(text).replace("\r", "").replace("\n", "<br/>");
    }

    static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    // characters XML cannot carry would make the renderer fail
                    if (c == '\n' || c == '\r' || c == '\t' || c >= 0x20) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
