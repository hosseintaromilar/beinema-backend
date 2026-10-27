package com.coupleai.coupleai.beinema.Service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.util.Optional;

/**
 * The text of a PDF that has real text in it, read here with PDFBox (already in
 * the project for the chat export): fast, free and exact. A scanned PDF (only
 * pictures of pages) has no text; then it is empty and the file is read by the
 * model instead.
 */
final class PdfText {

    private static final int MAX_PAGES = 30;
    /** Fewer letters than this: treat it as a scan. */
    private static final int MIN_LETTERS = 20;

    private PdfText() {
    }

    static Optional<String> extract(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            if (document.isEncrypted()) {
                return Optional.empty();
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PAGES));
            String text = stripper.getText(document)
                    .replace("\r", "")
                    .replaceAll("[ \\t]+\\n", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .strip();
            long letters = text.codePoints().filter(Character::isLetter).count();
            return letters >= MIN_LETTERS ? Optional.of(text) : Optional.empty();
        } catch (Exception | LinkageError unreadable) {
            return Optional.empty();
        }
    }
}
