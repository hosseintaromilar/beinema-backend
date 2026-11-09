package com.coupleai.coupleai.beinema.teach;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
