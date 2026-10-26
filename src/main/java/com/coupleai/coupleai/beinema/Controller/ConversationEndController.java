package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Feedback.FeedbackRequest;
import com.coupleai.coupleai.beinema.Service.ConversationEndService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ending a conversation for good, coming back to it, and the survey after it. */
@RestController
@RequestMapping("/api/chat-rooms/{id}")
@RequiredArgsConstructor
public class ConversationEndController {

    private final ConversationEndService conversationEndService;

    @PostMapping("/end")
    public ConversationEndService.EndResult end(@PathVariable Long id) {
        return conversationEndService.end(id);
    }

    @PostMapping("/rejoin")
    public ResponseEntity<Void> rejoin(@PathVariable Long id) {
        conversationEndService.rejoin(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(@PathVariable Long id, @RequestBody FeedbackRequest request) {
        conversationEndService.saveFeedback(id, request);
        return ResponseEntity.noContent().build();
    }
}
