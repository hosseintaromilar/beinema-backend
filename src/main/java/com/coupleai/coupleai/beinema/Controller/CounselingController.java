package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.counseling.AnswerQuestionRequest;
import com.coupleai.coupleai.beinema.DTO.counseling.QuestionResponse;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import com.coupleai.coupleai.beinema.counseling.CounselorStateService;
import com.coupleai.coupleai.beinema.counseling.QuestionService;
import com.coupleai.coupleai.beinema.counseling.QuestionViews;
import com.coupleai.coupleai.beinema.counseling.TurnRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Structured clarifying questions and the counselor's inner state (developer panel). */
@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}")
@RequiredArgsConstructor
public class CounselingController {

    private final QuestionService questionService;
    private final CounselorStateService counselorStateService;
    private final CurrentUserService currentUserService;
    private final ChatRealtimeService chatRealtimeService;
    private final ChatRoomEventHub chatRoomEventHub;
    private final TurnRunner turnRunner;
    private final com.coupleai.coupleai.beinema.counseling.PersonalPathService personalPathService;

    /**
     * The addressee answers a structured question. The answer is stored once
     * (a repeated submit with the same clientRequestId returns the same result),
     * appears in the chat, and the counselor continues from it.
     */
    @PostMapping("/questions/{questionId}/answer")
    public QuestionResponse answer(
            @PathVariable Long chatRoomId,
            @PathVariable String questionId,
            @RequestBody AnswerQuestionRequest request
    ) {
        User user = currentUserService.getCurrentUser();
        QuestionService.AnswerResult result = questionService.answer(chatRoomId, questionId, request, user);
        QuestionResponse view = QuestionViews.view(result.question(), user.visibleName());
        if (!result.repeated()) {
            if (result.answerMessage() != null) {
                chatRealtimeService.publish(chatRoomId, result.answerMessage());
            }
            chatRoomEventHub.publishNamed(chatRoomId, "question_updated", view);
            turnRunner.runAsync(chatRoomId);
        }
        return view;
    }

    /** The current user's own path (WDEP milestones); never the partner's. */
    @GetMapping("/my-path")
    public Map<String, Object> myPath(@PathVariable Long chatRoomId) {
        return personalPathService.forUser(chatRoomId, currentUserService.getCurrentUser());
    }

    @GetMapping("/counselor-state")
    public Map<String, Object> counselorState(@PathVariable Long chatRoomId) {
        return counselorStateService.describe(chatRoomId, currentUserService.getCurrentUser());
    }
}
