package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.AttachmentResponse;
import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPdfExporterTest {

    private static MessageResponse message(
            long id, MessageSenderType type, Long senderId, String name, String content, int minute) {
        return MessageResponse.builder()
                .id(id)
                .senderType(type)
                .senderId(senderId)
                .displayName(name)
                .senderName(name)
                .content(content)
                .createdAt(LocalDateTime.of(2026, 10, 20, 16, minute))
                .build();
    }

    private List<MessageResponse> sample() {
        List<MessageResponse> list = new ArrayList<>();
        list.add(message(1, MessageSenderType.SYSTEM, 3L, "", "حسین با لینک دعوت به گفتگو اضافه شد", 30));
        list.add(message(2, MessageSenderType.AGENT, 0L, "زوج درمانگر",
                "[[GOV|GOV=NONE|TARGET=NONE|DURATION=0]]\nافسانه، خوش اومدی. من مشاور این جلسه‌ام و اینجا هستم که به هر دوی شما کمک کنم.", 34));
        list.add(message(3, MessageSenderType.USER, 1L, "افسانه",
                "سلام، من و حسین چند وقته سر هر چیزی بحثمون می‌شه. ازش خواستم بیاد اینجا.", 35));
        list.add(message(4, MessageSenderType.USER, 3L, "حسین",
                "راستش؟ خسته شدم. هر کاری می‌کنم بده. می‌رم سر کار، بده. خونه می‌مونم، بده <ببین> & \"نقل‌قول\".", 36));
        list.add(message(5, MessageSenderType.AGENT, 0L, "زوج درمانگر",
                "حسین، انگار واقعاً خسته‌ای.\n\nدوست داری بگی چی بیشتر از همه اذیتت می‌کنه؟ Test 123 english word.", 37));
        list.add(message(6, MessageSenderType.AGENT, 0L, "زوج درمانگر", "🔧 DIR: ACTION=REFLECT|ROUTE=SPEAK", 37));
        MessageResponse withFile = message(7, MessageSenderType.USER, 1L, "افسانه", "این هم عکس", 40);
        withFile.setAttachments(List.of(AttachmentResponse.builder().id(1L).name("screenshot.png").build()));
        list.add(withFile);
        MessageResponse sticker = message(8, MessageSenderType.AGENT, 0L, "زوج درمانگر", "LISTEN", 41);
        sticker.setSticker(true);
        list.add(sticker);
        MessageResponse reply = message(9, MessageSenderType.USER, 3L, "حسین", "درسته، همینو می‌گم.", 42);
        reply.setReplyExcerpt("انگار واقعاً خسته‌ای.");
        reply.setReplyToSender("زوج درمانگر");
        list.add(reply);
        for (int i = 0; i < 30; i++) {
            list.add(message(20 + i, i % 2 == 0 ? MessageSenderType.USER : MessageSenderType.AGENT,
                    i % 2 == 0 ? 1L : 0L, i % 2 == 0 ? "افسانه" : "زوج درمانگر",
                    "پیام شمارهٔ " + i + " برای آزمودن صفحه‌بندی؛ " + "متن بلند ".repeat(12), 45));
        }
        return list;
    }

    @Test
    void rendersAPersianChatAsAPdf() throws Exception {
        ChatPdfExporter exporter = new ChatPdfExporter();
        byte[] pdf = exporter.render(
                new ChatPdfExporter.Header("پارتنر بدبین به مشاوره", List.of("افسانه", "حسین", "زوج درمانگر"),
                        "زوج درمانگر", "افسانه", 1),
                sample(), 1L);

        assertTrue(pdf.length > 5000, "a real document, not an empty one");
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));

        Path out = Path.of("target", "chat-export-sample.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);

        try (PDDocument document = PDDocument.load(pdf)) {
            assertTrue(document.getNumberOfPages() >= 2, "long chats continue on the next page");
            // a picture of the first page, to look at it
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 90, ImageType.RGB), "png",
                    new File("target/chat-export-page1.png"));
            java.awt.image.BufferedImage big = new PDFRenderer(document).renderImageWithDPI(0, 260, ImageType.RGB);
            ImageIO.write(big.getSubimage(big.getWidth() / 8, (int) (big.getHeight() * 0.37),
                    (int) (big.getWidth() * 0.78), (int) (big.getHeight() * 0.22)), "png",
                    new File("target/chat-export-zoom.png"));
        }
    }

    @Test
    void markersDebugLinesAndHtmlSpecialsNeverReachTheDocument() {
        ChatPdfExporter exporter = new ChatPdfExporter();
        String html = exporter.html(
                new ChatPdfExporter.Header("اتاق <۱>", List.of("افسانه"), "", "", 1), sample(), 1L);

        assertFalse(html.contains("[[GOV"), "control markers are removed");
        assertFalse(html.contains("DIR: ACTION"), "temporary debug lines are left out");
        assertTrue(html.contains("&lt;ببین&gt; &amp;"), "special characters are escaped");
        assertTrue(html.contains("اتاق &lt;۱&gt;"));
        assertTrue(html.contains("class=\"msg me\""), "the viewer's own messages are marked");
        assertTrue(html.contains("class=\"msg partner\""));
        assertTrue(html.contains("class=\"msg agent\""));
        assertTrue(html.contains("پیوست: screenshot.png"));
        assertTrue(html.contains("(استیکر مشاور)"));
        assertTrue(html.contains("انگار واقعاً خسته‌ای."));
    }

    @Test
    void anEmptyChatStillMakesADocument() throws Exception {
        byte[] pdf = new ChatPdfExporter().render(
                new ChatPdfExporter.Header("گفتگوی خالی", List.of(), "", "", 0), List.of(), 1L);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }
}
