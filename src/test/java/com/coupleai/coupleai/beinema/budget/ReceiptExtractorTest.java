package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import com.coupleai.coupleai.beinema.files.ChatFileService;
import com.coupleai.coupleai.beinema.metis.MetisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptExtractorTest {

    private final MetisService metis = mock(MetisService.class);
    private final ReceiptExtractor extractor = new ReceiptExtractor(metis, new ObjectMapper(), "reader");

    @Test
    void convertsRialsAndJalaliDatesUsingBudgetConventions() {
        ReceiptExtractor.Draft draft = extractor.parse("""
                {"amount":"۱۲۳٬۴۵۰","currency":"IRR","date":"۱۴۰۵/۰۸/۱۶","calendar":"JALALI",
                 "category":"FOOD","note":"خرید خوراک"}
                """);

        assertEquals("12345", draft.amount());
        assertEquals("IRR", draft.currency());
        assertEquals("2026-11-07", draft.date());
        assertEquals("FOOD", draft.category());
        assertEquals("خرید خوراک", draft.note());
        assertNull(draft.warning());
    }

    @Test
    void leavesAmbiguousAndInvalidValuesForManualEntry() {
        ReceiptExtractor.Draft draft = extractor.parse("""
                {"amount":"999,995","currency":"IRR","date":"1405-07-31","calendar":"JALALI",
                 "category":"SALARY","note":"card 123456","extra":"not a budget field"}
                """);

        assertNull(draft.amount());
        assertNull(draft.category());
        assertNull(draft.date());
        assertNull(draft.note());
        assertNotNull(draft.warning());
        assertNull(extractor.parse("{\"amount\":\"999\",\"category\":\"FOOD\"}").amount());
    }

    @Test
    void rejectsMoreThanOneRootAndDuplicateFields() {
        assertNull(extractor.parse("{\"amount\":\"123\",\"currency\":\"IRT\"} {}").amount());
        assertNull(extractor.parse("{\"amount\":\"123\",\"amount\":\"456\",\"currency\":\"IRT\"}").amount());
        assertNull(extractor.parse("```json\n{\"amount\":\"123\",\"currency\":\"IRT\"}\n```").amount());
    }

    @Test
    void uploadsAnImageWithANeutralFilenameAndForgetsTheReaderSession() {
        byte[] image = {1, 2, 3};
        MessageAttachment attachment = new MessageAttachment();
        attachment.setContentType("image/png");
        attachment.setOriginalName("sensitive name.png");
        MetisConversationResponse conversation = new MetisConversationResponse();
        conversation.setId("session");
        when(metis.uploadFile(image, "receipt.png", "image/png")).thenReturn("attachment");
        when(metis.createConversation(eq("reader"), anyString(), eq("Receipt reader"))).thenReturn(conversation);
        when(metis.sendMessageWithAttachments(eq("session"), anyString(), anyList()))
                .thenReturn("{\"amount\":\"8000\",\"currency\":\"IRT\"}");

        ReceiptExtractor.Draft draft = extractor.extract(
                new ChatFileService.LoadedFile(new ByteArrayResource(image), attachment));

        assertEquals("8000", draft.amount());
        verify(metis).forget("session");
    }

    @Test
    void sendsTextFromTextPdfAndUsesTheFileForATextlessPdf() throws IOException {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setContentType("application/pdf");
        MetisConversationResponse conversation = new MetisConversationResponse();
        conversation.setId("session");
        when(metis.createConversation(eq("reader"), anyString(), eq("Receipt reader"))).thenReturn(conversation);
        when(metis.sendMessage(eq("session"), contains("Total paid 8000 tomans")))
                .thenReturn("{\"amount\":\"8000\",\"currency\":\"IRT\"}");

        byte[] textPdf = pdfWithText("Grocery store receipt - Total paid 8000 tomans");
        var draft = extractor.extract(new ChatFileService.LoadedFile(new ByteArrayResource(textPdf), attachment));
        assertEquals("8000", draft.amount());
        verify(metis, never()).uploadFile(textPdf, "receipt.pdf", "application/pdf");

        byte[] textlessPdf = pdfWithText(null);
        when(metis.uploadFile(textlessPdf, "receipt.pdf", "application/pdf")).thenReturn("file");
        when(metis.sendMessageWithAttachments(eq("session"), anyString(), anyList()))
                .thenReturn("{\"amount\":\"9000\",\"currency\":\"IRT\"}");
        draft = extractor.extract(new ChatFileService.LoadedFile(new ByteArrayResource(textlessPdf), attachment));
        assertEquals("9000", draft.amount());
        verify(metis).uploadFile(textlessPdf, "receipt.pdf", "application/pdf");
    }

    private static byte[] pdfWithText(String text) throws IOException {
        try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            if (text != null) {
                try (var content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA, 12);
                    content.newLineAtOffset(40, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }
}
