package com.coupleai.coupleai.beinema.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceMessageTest {

    private static byte[] head(int... start) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < start.length; i++) {
            bytes[i] = (byte) start[i];
        }
        return bytes;
    }

    @Test
    void whatBrowsersRecordIsKnownFromItsBytes() {
        assertEquals("audio/webm", ChatFileService.detectAudio(head(0x1A, 0x45, 0xDF, 0xA3)).orElseThrow().contentType());
        assertEquals("audio/ogg", ChatFileService.detectAudio("OggS........".getBytes(StandardCharsets.US_ASCII)).orElseThrow().contentType());
        assertEquals("audio/mp4", ChatFileService.detectAudio("\0\0\0 ftypM4A ....".getBytes(StandardCharsets.US_ASCII)).orElseThrow().contentType());
        assertEquals("AUDIO", ChatFileService.detectAudio("ID3\u0004........".getBytes(StandardCharsets.US_ASCII)).orElseThrow().kind());
        assertTrue(ChatFileService.detectAudio("%PDF-1.7 .......".getBytes(StandardCharsets.US_ASCII)).isEmpty(), "a document is no voice");
        assertTrue(ChatFileService.detectAudio(head(0xFF, 0xD8, 0xFF)).isEmpty(), "a photo is no voice");
    }

    @Test
    void theWordsComeFromTheReplyAndSilenceIsNothing() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String said = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" سلام، امروز خیلی خسته‌ام. \"}]}}]}";
        assertEquals("سلام، امروز خیلی خسته‌ام.", SpeechToText.text(json.readTree(said)).orElseThrow());
        assertTrue(SpeechToText.text(json.readTree("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"-\"}]}}]}")).isEmpty());
        assertTrue(SpeechToText.text(json.readTree("{\"error\":{}}")).isEmpty());
    }
}
