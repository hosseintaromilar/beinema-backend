package com.coupleai.coupleai.beinema.Service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFileServiceTest {

    @Test
    void imagesAndPdfAreRecognisedFromTheirBytes() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};
        byte[] pdf = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

        assertEquals("image/jpeg", ChatFileService.detect(jpeg).orElseThrow().contentType());
        assertEquals("image/png", ChatFileService.detect(png).orElseThrow().contentType());
        assertEquals("image/webp", ChatFileService.detect(webp).orElseThrow().contentType());
        assertEquals("application/pdf", ChatFileService.detect(pdf).orElseThrow().contentType());
        assertEquals("IMAGE", ChatFileService.detect(jpeg).orElseThrow().kind());
        assertEquals("FILE", ChatFileService.detect(pdf).orElseThrow().kind());
    }

    @Test
    void persianTextIsAcceptedAsPlainText() {
        byte[] text = "سلام، این یادداشت من است.".getBytes(StandardCharsets.UTF_8);
        assertEquals("text/plain", ChatFileService.detect(text).orElseThrow().contentType());
    }

    @Test
    void executablesAndBinaryBlobsAreRejected() {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        byte[] zip = {'P', 'K', 3, 4, 0, 0, 0, 0};
        assertTrue(ChatFileService.detect(exe).isEmpty());
        assertTrue(ChatFileService.detect(zip).isEmpty());
        assertTrue(ChatFileService.detect(new byte[]{1, 2}).isEmpty());
    }

    /** A minimal Word file: a zip with word/document.xml. */
    private static byte[] docx(String documentXml) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void aWordFileIsAcceptedAndItsTextIsRead() throws Exception {
        byte[] word = docx("<w:document><w:body>"
                + "<w:p><w:r><w:t>سلام، این یادداشت من است.</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>دعوای دیشب &amp; حرف‌هایی</w:t></w:r><w:r><w:tab/><w:t>که نزدیم</w:t></w:r></w:p>"
                + "</w:body></w:document>");

        assertEquals(ChatFileService.DOCX, ChatFileService.detect(word).orElseThrow().contentType());
        assertEquals(".docx", ChatFileService.detect(word).orElseThrow().extension());
        assertEquals("سلام، این یادداشت من است.\nدعوای دیشب & حرف‌هایی\tکه نزدیم",
                DocxText.extract(word).orElseThrow());
    }

    private static byte[] pdf(String line) throws java.io.IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            if (line != null) {
                try (org.apache.pdfbox.pdmodel.PDPageContentStream content =
                             new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 14);
                    content.newLineAtOffset(40, 700);
                    content.showText(line);
                    content.endText();
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void aPdfWithTextIsReadHereAndAScanIsLeftToTheModel() throws Exception {
        byte[] withText = pdf("Our weekly plan: we talk every Friday evening.");
        assertEquals("Our weekly plan: we talk every Friday evening.", PdfText.extract(withText).orElseThrow());
        assertTrue(PdfText.extract(pdf(null)).isEmpty(), "a page without text is a scan for the model");
        assertTrue(PdfText.extract("not a pdf".getBytes(StandardCharsets.UTF_8)).isEmpty());
    }

    @Test
    void theOldWordFormatIsRecognisedSoThePersonIsToldToConvertIt() {
        byte[] doc = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        assertTrue(DocxText.isOldWord(doc));
        assertTrue(ChatFileService.detect(doc).isEmpty());
    }
}
