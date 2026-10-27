package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface MessageService {

    MessageResponse sendMessage(
            Long chatRoomId,
            SendMessageRequest request
    );

    List<MessageResponse> getMessages(
            Long chatRoomId
    );

    SseEmitter sendMessageStream(
            Long chatRoomId,
            SendMessageRequest request
    );

    SseEmitter subscribe(
            Long chatRoomId
    );

    void submitReentryCommitment(
            Long chatRoomId,
            boolean accepted
    );

    void submitSessionConsent(
            Long chatRoomId,
            boolean accepted
    );

    void announceEntry(Long chatRoomId);

    /** The rules box on the chat page was accepted: the short welcome and the entry follow. */
    void acceptRules(Long chatRoomId);
}
