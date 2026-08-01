
package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;
import com.coupleai.coupleai.beinema.Service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

        import java.util.List;

@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public List<MessageResponse> getMessages(
            @PathVariable Long chatRoomId
    ) {

        return messageService.getMessages(chatRoomId);

    }

    @PostMapping
    public MessageResponse sendMessage(

            @PathVariable Long chatRoomId,

            @Valid
            @RequestBody
            SendMessageRequest request

    ) {

        return messageService.sendMessage(
                chatRoomId,
                request
        );

    }

}