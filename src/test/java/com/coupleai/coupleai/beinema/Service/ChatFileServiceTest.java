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
}
