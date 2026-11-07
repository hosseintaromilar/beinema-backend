package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import com.coupleai.coupleai.beinema.counseling.FinanceService;
import com.coupleai.coupleai.beinema.counseling.TurnRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The financial companion's cards in a room: see one, answer the quiz, save the picture, choose a scenario. */
@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final CurrentUserService currentUserService;
    private final ChatRealtimeService chatRealtimeService;
    private final ChatRoomEventHub chatRoomEventHub;
    private final TurnRunner turnRunner;

    @GetMapping("/{cardId}")
    public Map<String, Object> card(@PathVariable Long chatRoomId, @PathVariable String cardId) {
        return financeService.view(chatRoomId, cardId, currentUserService.getCurrentUser());
    }

    @PostMapping("/{cardId}/risk")
    public Map<String, Object> answerRisk(@PathVariable Long chatRoomId, @PathVariable String cardId,
                                          @RequestBody Map<String, Integer> answers) {
        User user = currentUserService.getCurrentUser();
        return after(chatRoomId, cardId, user, financeService.answerRisk(chatRoomId, cardId, answers, user));
    }

    @PostMapping("/{cardId}/picture")
    public Map<String, Object> savePicture(@PathVariable Long chatRoomId, @PathVariable String cardId,
                                           @RequestBody Map<String, Object> body) {
        User user = currentUserService.getCurrentUser();
        return after(chatRoomId, cardId, user, financeService.savePicture(chatRoomId, cardId, body, user));
    }

    @PostMapping("/{cardId}/choice")
    public Map<String, Object> choose(@PathVariable Long chatRoomId, @PathVariable String cardId,
                                      @RequestBody Map<String, Integer> body) {
        User user = currentUserService.getCurrentUser();
        Integer index = body.get("index");
        return after(chatRoomId, cardId, user, financeService.choose(chatRoomId, cardId, index == null ? -1 : index, user));
    }

    /** What a person did is a message of the conversation: everyone sees it and the companion goes on from it. */
    private Map<String, Object> after(Long chatRoomId, String cardId, User user, FinanceService.Done done) {
        if (done.message() != null) {
            chatRealtimeService.publish(chatRoomId, done.message());
            chatRoomEventHub.publishNamed(chatRoomId, "test_updated", Map.of("id", cardId));
            turnRunner.runAsync(chatRoomId);
        }
        return financeService.view(chatRoomId, cardId, user);
    }
}
