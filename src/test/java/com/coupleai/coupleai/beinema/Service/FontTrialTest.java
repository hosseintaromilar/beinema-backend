package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

/** Dev only: renders one Persian paragraph with each candidate font, to compare the letter shapes. */
class FontTrialTest {

    @Test
    void renderCandidates() throws Exception {
        String dir = System.getProperty("fontDir");
        if (dir == null) {
            return;
        }
        String[][] fonts = {
                {"vazirmatn", "Vazirmatn-Regular.ttf", "Vazirmatn-Bold.ttf"},
                {"vazir", "Vazir-Regular.ttf", "Vazir-Bold.ttf"}
        };
        String text = "سلام، من و حسین چند وقته سر هر چیزی بحثمون می‌شه. این اینجا بیشتر دیدی کیفیت پیگیری میخواد اذیتت نمی‌کنه گفتگو بین‌ما.";
        for (String[] font : fonts) {
            MessageResponse message = MessageResponse.builder()
                    .id(1L).senderType(MessageSenderType.AGENT).senderId(0L).displayName("زوج درمانگر")
                    .content(text).createdAt(LocalDateTime.of(2026, 10, 20, 16, 34)).build();
            byte[] pdf = new ChatPdfExporter(new File(dir, font[1]).getPath(), new File(dir, font[2]).getPath())
                    .render(new ChatPdfExporter.Header("گفتگوی آزمایشی", List.of("افسانه", "حسین"), "زوج درمانگر", "افسانه", 1),
                            List.of(message), 1L);
            try (PDDocument doc = PDDocument.load(pdf)) {
                BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 220, ImageType.RGB);
                int w = page.getWidth();
                ImageIO.write(page.getSubimage(w / 12, (int) (page.getHeight() * 0.04), (int) (w * 0.84), (int) (page.getHeight() * 0.30)),
                        "png", new File("target/font-trial-" + font[0] + ".png"));
            }
        }
    }
}
