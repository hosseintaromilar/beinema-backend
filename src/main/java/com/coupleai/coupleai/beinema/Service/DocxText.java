package com.coupleai.coupleai.beinema.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The text of a Word file (.docx). A docx is a zip whose word/document.xml holds
 * the paragraphs; this reads that one entry (with a size limit, so a crafted
 * zip cannot blow up) and keeps the words, one paragraph per line.
 */
final class DocxText {

    /** The old binary Word format (.doc) starts with the OLE signature. */
    static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final long MAX_XML_BYTES = 8L * 1024 * 1024;

    private DocxText() {
    }

    static boolean isZip(byte[] bytes) {
        return bytes != null && bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
    }

    static boolean isOldWord(byte[] bytes) {
        if (bytes == null || bytes.length < OLE.length) {
            return false;
        }
        for (int i = 0; i < OLE.length; i++) {
            if (bytes[i] != OLE[i]) {
                return false;
            }
        }
        return true;
    }

    /** The text of the document, or empty when this zip is not a Word file. */
    static Optional<String> extract(byte[] bytes) {
        if (!isZip(bytes)) {
            return Optional.empty();
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_XML_BYTES) {
                        return Optional.empty();
                    }
                    out.write(buffer, 0, read);
                }
                return Optional.of(text(out.toString(StandardCharsets.UTF_8)));
            }
            return Optional.empty();
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Paragraphs and breaks become new lines, tabs stay, every other tag goes. */
    static String text(String xml) {
        String withBreaks = xml
                .replaceAll("</w:p>", "\n")
                .replaceAll("<w:br[^>]*/>", "\n")
                .replaceAll("<w:tab[^>]*/>", "\t");
        String plain = withBreaks.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
        return plain.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }
}
