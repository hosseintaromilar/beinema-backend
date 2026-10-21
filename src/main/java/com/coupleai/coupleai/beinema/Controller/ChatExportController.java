package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Service.ChatExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Download of a whole chat as a PDF file. */
@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}/export")
@RequiredArgsConstructor
public class ChatExportController {

    private final ChatExportService chatExportService;

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long chatRoomId) throws IOException {
        ChatExportService.Exported exported = chatExportService.exportPdf(chatRoomId);

        // the title is Persian: send it in the standard encoded form, with a plain fallback name
        String title = exported.title() == null || exported.title().isBlank() ? "گفتگو" : exported.title().trim();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("بین‌ما - " + title + ".pdf", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(exported.bytes());
    }
}
