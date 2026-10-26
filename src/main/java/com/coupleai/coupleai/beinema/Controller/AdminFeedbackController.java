package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Feedback.FeedbackView;
import com.coupleai.coupleai.beinema.Service.ConversationEndService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The surveys people filled in when they ended a conversation (admins only, see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final ConversationEndService conversationEndService;

    @GetMapping
    public List<FeedbackView> list() {
        return conversationEndService.allFeedback();
    }
}
