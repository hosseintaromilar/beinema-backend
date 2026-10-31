package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Service.SessionLifecycleService;
import com.coupleai.coupleai.beinema.governance.WelcomeGuide;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The rules and how the work goes, shown with the «I accept» checkbox before a
 * person creates a room or joins one by invitation. Public: the invitation page
 * shows them before signing in too.
 */
@RestController
@RequiredArgsConstructor
public class RulesController {

    private final SessionLifecycleService sessionLifecycleService;

    @GetMapping("/api/rules")
    public Map<String, String> rules(@org.springframework.web.bind.annotation.RequestParam(required = false) String agentType) {
        return Map.of("text", WelcomeGuide.rules(
                sessionLifecycleService.defaultSessionMinutes(),
                sessionLifecycleService.breakMinutes(),
                sessionLifecycleService.overtimeMinutes(),
                sessionLifecycleService.maxSessions(),
                // the framework of the couple counselor is hers alone; the others work by their own method
                "RELATIONSHIP".equals(agentType)));
    }
}
