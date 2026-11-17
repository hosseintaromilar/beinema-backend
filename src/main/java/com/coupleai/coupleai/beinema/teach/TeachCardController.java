package com.coupleai.coupleai.beinema.teach;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The lesson cards: one card, its question, and «درس‌های من». Each only for the person it was made for. */
@RestController
@RequiredArgsConstructor
public class TeachCardController {

    private final TeachCardService teachCards;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/teach-cards/{publicId}")
    public Map<String, Object> card(@PathVariable String publicId) {
        return teachCards.view(publicId, currentUserService.getCurrentUser());
    }

    /** Body: { "choice": 1 } */
    @PostMapping("/api/teach-cards/{publicId}/answer")
    public Map<String, Object> answer(@PathVariable String publicId, @RequestBody Map<String, Integer> body) {
        Integer choice = body == null ? null : body.get("choice");
        if (choice == null) {
            throw new IllegalArgumentException("یکی از گزینه‌ها را انتخاب کن.");
        }
        return teachCards.answer(publicId, currentUserService.getCurrentUser(), choice);
    }

    @GetMapping("/api/teach-cards")
    public List<Map<String, Object>> mine() {
        return teachCards.mine(currentUserService.getCurrentUser());
    }

    /** Body: { "note": "what they did and saw" }: the exercise of a lesson after a session is done. */
    @PostMapping("/api/teach-cards/{publicId}/exercise")
    public Map<String, Object> exercise(@PathVariable String publicId, @RequestBody Map<String, String> body) {
        return teachCards.exercise(publicId, currentUserService.getCurrentUser(), body == null ? null : body.get("note"));
    }

    @PostMapping("/api/teach-cards/{publicId}/seen")
    public Map<String, Object> seen(@PathVariable String publicId) {
        teachCards.seen(publicId, currentUserService.getCurrentUser());
        return Map.of("status", "OK");
    }

    /** The lessons after a session not opened yet, for the conversation cards of the dashboard. */
    @GetMapping("/api/teach-cards/fresh")
    public List<Map<String, Object>> fresh() {
        return teachCards.fresh(currentUserService.getCurrentUser());
    }

    /** Whether a lesson is made for this person after each session (on unless they turned it off). */
    @GetMapping("/api/teach-cards/settings")
    public Map<String, Object> settings() {
        return Map.of("afterSession", !Boolean.TRUE.equals(currentUserService.getCurrentUser().getSessionLessonsOff()));
    }

    /** Body: { "afterSession": false } */
    @PutMapping("/api/teach-cards/settings")
    public Map<String, Object> settings(@RequestBody Map<String, Boolean> body) {
        boolean on = body == null || !Boolean.FALSE.equals(body.get("afterSession"));
        teachCards.setAfterSession(currentUserService.getCurrentUser(), on);
        return Map.of("afterSession", on);
    }
}
