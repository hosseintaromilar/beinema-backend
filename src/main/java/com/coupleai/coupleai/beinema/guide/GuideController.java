package com.coupleai.coupleai.beinema.guide;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The general guide's chat: open to everyone, logged in or not. Nothing of it is stored. */
@RestController
@RequestMapping("/api/guide")
@RequiredArgsConstructor
public class GuideController {

    private final GuideService guideService;
    private final CurrentUserService currentUserService;

    public record Ask(List<GuideService.Turn> history, String message) {
    }

    @PostMapping("/messages")
    public GuideService.Answer ask(@RequestBody Ask body, HttpServletRequest request) {
        return guideService.ask(body.history(), body.message(), currentUser(), address(request));
    }

    private User currentUser() {
        try {
            return currentUserService.getCurrentUser();
        } catch (RuntimeException anonymous) {
            return null;
        }
    }

    /** The visitor's address as Caddy passes it on; the first one is the client. */
    private static String address(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
