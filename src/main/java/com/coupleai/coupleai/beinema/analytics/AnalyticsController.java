package com.coupleai.coupleai.beinema.analytics;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The visit log: the site's two open doors (a page was opened, the tab is still here) and the
 * admin's report. A failure to count never shows on the site: the doors always answer 204.
 */
@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final CurrentUserService currentUserService;

    public record Ping(String sid, String path) {
    }

    @PostMapping("/api/analytics/view")
    public ResponseEntity<Void> view(@RequestBody AnalyticsService.Hit hit, HttpServletRequest request) {
        try {
            analytics.view(hit, request.getHeader("User-Agent"), address(request), userId());
        } catch (RuntimeException ignored) {
            // counting is best effort; the visitor's page is never affected
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/analytics/ping")
    public ResponseEntity<Void> ping(@RequestBody Ping ping, HttpServletRequest request) {
        analytics.ping(ping.sid(), ping.path(), request.getHeader("User-Agent"), address(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/analytics")
    public Map<String, Object> report(@RequestParam(defaultValue = "7d") String range) {
        return analytics.report(range);
    }

    @GetMapping("/api/admin/analytics/live")
    public Map<String, Object> live() {
        return analytics.live();
    }

    private Long userId() {
        try {
            return currentUserService.getCurrentUser().getId();
        } catch (RuntimeException anonymous) {
            return null;
        }
    }

    /** The visitor's address as Caddy passes it on, only for flood control in memory. */
    private static String address(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
