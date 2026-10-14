package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.messages.AttachmentResponse;
import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import com.coupleai.coupleai.beinema.Service.ChatFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}/files")
@RequiredArgsConstructor
public class ChatFileController {

    private final ChatFileService chatFileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse upload(
            @PathVariable Long chatRoomId,
            @RequestParam("file") MultipartFile file
    ) {
        return chatFileService.upload(chatRoomId, file);
    }

    /** Only members of the room can read a file. Images show inline, everything else downloads. */
    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long chatRoomId,
            @PathVariable Long attachmentId
    ) {
        ChatFileService.LoadedFile loaded = chatFileService.load(chatRoomId, attachmentId);
        MessageAttachment attachment = loaded.attachment();
        boolean inline = "IMAGE".equals(attachment.getKind());
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(attachment.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(loaded.resource());
    }
}
