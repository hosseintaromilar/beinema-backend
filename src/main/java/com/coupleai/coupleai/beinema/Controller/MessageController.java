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
    private final com.coupleai.coupleai.beinema.Service.MessageReceiptService receiptService;

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

    @PostMapping("/{chatRoomId}/reentry-commitment")
    public void submitReentryCommitment(
            @PathVariable Long chatRoomId,
            @RequestBody com.coupleai.coupleai.beinema.DTO.messages.ReentryCommitmentRequest request
    ) {
        boolean accepted = request != null && Boolean.TRUE.equals(request.getAccepted());
        messageService.submitReentryCommitment(chatRoomId, accepted);
    }

    @PostMapping("/{chatRoomId}/session-consent")
    public void submitSessionConsent(
            @PathVariable Long chatRoomId,
            @RequestBody com.coupleai.coupleai.beinema.DTO.messages.ReentryCommitmentRequest request
    ) {
        boolean accepted = request != null && Boolean.TRUE.equals(request.getAccepted());
        messageService.submitSessionConsent(chatRoomId, accepted);
    }

    /** How far this page has received and read: the source of the other side's ticks. */
    @PostMapping("/{chatRoomId}/receipts")
    public void receipts(@PathVariable Long chatRoomId, @RequestBody java.util.Map<String, Integer> body) {
        receiptService.acknowledge(chatRoomId, body.get("deliveredSeq"), body.get("readSeq"));
    }

    @PostMapping("/{chatRoomId}/entry")
    public void announceEntry(@PathVariable Long chatRoomId) {
        messageService.announceEntry(chatRoomId);
    }

    /** The rules box on the chat page (someone added without the invitation page, or an older room). */
    @PostMapping("/{chatRoomId}/rules-accept")
    public void acceptRules(@PathVariable Long chatRoomId) {
        messageService.acceptRules(chatRoomId);
    }
}
