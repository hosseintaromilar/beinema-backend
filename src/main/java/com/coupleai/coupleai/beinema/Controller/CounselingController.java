package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.counseling.AnswerQuestionRequest;
import com.coupleai.coupleai.beinema.DTO.counseling.QuestionResponse;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import com.coupleai.coupleai.beinema.counseling.CoupleTestService;
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
    private final com.coupleai.coupleai.beinema.counseling.ConfidentialMessageService confidentialMessageService;
    private final com.coupleai.coupleai.beinema.Service.SessionModeService sessionModeService;
    private final com.coupleai.coupleai.beinema.counseling.SessionEndCheckService endCheckService;
    private final com.coupleai.coupleai.beinema.Service.ConversationEndService conversationEndService;
    private final CoupleTestService coupleTestService;
    private final com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository chatRoomAgentRepository;
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

    /** A test of the companion's kit, as this person may see it. */
    @GetMapping("/tests/{testId}")
    public Map<String, Object> test(@PathVariable Long chatRoomId, @PathVariable String testId) {
        return coupleTestService.view(chatRoomId, testId, currentUserService.getCurrentUser());
    }

    /** One person's answers. The result appears when the second person has answered too. */
    @PostMapping("/tests/{testId}/answers")
    public Map<String, Object> submitTest(
            @PathVariable Long chatRoomId,
            @PathVariable String testId,
            @RequestBody Map<String, Integer> answers
    ) {
        User user = currentUserService.getCurrentUser();
        CoupleTestService.Submitted result = coupleTestService.submit(chatRoomId, testId, answers, user);
        if (!result.repeated()) {
            chatRealtimeService.publish(chatRoomId, result.message());
            if (result.fearNotice()) {
                chatRealtimeService.savePrivateSystemAndPublish(chatRoomId, user.getId(), CoupleTestService.FEAR_NOTICE);
            }
            if (result.finishedNow()) {
                Long agentId = chatRoomAgentRepository.findActiveAgentIds(chatRoomId).stream().findFirst().orElse(0L);
                chatRealtimeService.saveAgentAndPublish(chatRoomId, agentId, com.coupleai.coupleai.beinema.governance.KitMarkers.content(
                        com.coupleai.coupleai.beinema.governance.KitMarkers.RESULT, testId, "نتیجهٔ آزمون"));
            }
            chatRoomEventHub.publishNamed(chatRoomId, "test_updated", Map.of("id", testId));
            turnRunner.runAsync(chatRoomId);
        }
        return coupleTestService.view(chatRoomId, testId, user);
    }

    /** A sealed message to the companion: the answer comes as a sealed message too. */
    @PostMapping("/private-messages")
    public com.coupleai.coupleai.beinema.DTO.messages.MessageResponse sendPrivate(
            @PathVariable Long chatRoomId,
            @RequestBody Map<String, String> body
    ) {
        User user = currentUserService.getCurrentUser();
        return com.coupleai.coupleai.beinema.DTO.messages.MessageResponse.from(
                confidentialMessageService.send(chatRoomId, body.get("content"), user));
    }

    @GetMapping("/session-mode")
    public Map<String, Object> sessionMode(@PathVariable Long chatRoomId) {
        return sessionModeService.view(chatRoomId, currentUserService.getCurrentUser());
    }

    @PostMapping("/session-mode")
    public Map<String, Object> chooseSessionMode(@PathVariable Long chatRoomId, @RequestBody Map<String, String> body) {
        return sessionModeService.choose(chatRoomId, body.get("mode"), currentUserService.getCurrentUser());
    }

    /** The question whether to end the conversation here, as this person may see it. */
    @GetMapping("/end-checks/{checkId}")
    public Map<String, Object> endCheck(@PathVariable Long chatRoomId, @PathVariable String checkId) {
        return endCheckService.view(chatRoomId, checkId, currentUserService.getCurrentUser());
    }

    @PostMapping("/end-checks/{checkId}/vote")
    public Map<String, Object> voteEnd(
            @PathVariable Long chatRoomId,
            @PathVariable String checkId,
            @RequestBody Map<String, Boolean> body
    ) {
        User user = currentUserService.getCurrentUser();
        var vote = endCheckService.vote(chatRoomId, checkId, Boolean.TRUE.equals(body.get("yes")), user);
        if (vote.message() != null) {
            chatRealtimeService.publish(chatRoomId, vote.message());
        }
        chatRoomEventHub.publishNamed(chatRoomId, "test_updated", Map.of("id", checkId));
        if (vote.ended()) {
            Long agentId = chatRoomAgentRepository.findActiveAgentIds(chatRoomId).stream().findFirst().orElse(0L);
            conversationEndService.endByAgreement(chatRoomId, agentId);
        } else if (vote.continued()) {
            turnRunner.runAsync(chatRoomId);
        }
        return endCheckService.view(chatRoomId, checkId, user);
    }

    /** The card of a lesson of the kit. */
    @GetMapping("/lessons/{lessonId}")
    public Map<String, Object> lesson(@PathVariable Long chatRoomId, @PathVariable String lessonId) {
        return com.coupleai.coupleai.beinema.counseling.ElikaKit.lessonCard(lessonId)
                .orElseThrow(() -> new IllegalArgumentException("درس پیدا نشد."));
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
