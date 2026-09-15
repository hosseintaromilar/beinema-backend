package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;
import com.coupleai.coupleai.beinema.Service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/{chatRoomId}/messages")
    public List<MessageResponse> getMessages(
            @PathVariable Long chatRoomId
    ) {

        return messageService.getMessages(chatRoomId);
    }

    @GetMapping(
            value = "/{chatRoomId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe(
            @PathVariable Long chatRoomId
    ) {

        return messageService.subscribe(chatRoomId);
    }

    @PostMapping(
            value = "/{chatRoomId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter sendMessage(
            @PathVariable Long chatRoomId,
            @RequestBody SendMessageRequest request
    ) {

        return messageService.sendMessageStream(
                chatRoomId,
                request
        );
    }
}
