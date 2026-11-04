package com.coupleai.coupleai.beinema.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits guessing passwords and making accounts in bulk: requests to the sign-in routes
 * are counted per address in a time window. Many people in Iran share one address, so
 * the limits leave room for real use. The counts live in memory; a restart clears them.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private record Limit(int max, long windowMillis) {
    }

    private static final Map<String, Limit> LIMITS = Map.ofEntries(
            Map.entry("/api/auth/login", new Limit(20, 60_000)),
            Map.entry("/api/auth/google", new Limit(20, 60_000)),
            Map.entry("/api/auth/register", new Limit(10, 600_000)),
            Map.entry("/api/auth/forgot-password", new Limit(5, 600_000)),
            Map.entry("/api/auth/reset-password", new Limit(10, 600_000)),
            Map.entry("/api/auth/otp/request", new Limit(5, 600_000)),
            Map.entry("/api/auth/otp/verify", new Limit(20, 600_000)),
            Map.entry("/api/auth/email/start", new Limit(10, 600_000)),
            Map.entry("/api/auth/email/verify", new Limit(20, 600_000)),
            Map.entry("/api/auth/email/complete", new Limit(10, 600_000)),
            Map.entry("/api/auth/signup", new Limit(10, 600_000)),
            Map.entry("/api/auth/verify-email", new Limit(20, 600_000)),
            Map.entry("/api/auth/resend-code", new Limit(10, 600_000)),
            Map.entry("/api/auth/email/forgot-password", new Limit(5, 600_000)),
            Map.entry("/api/auth/email/reset-password", new Limit(10, 600_000))
    );
    private static final int MAX_TRACKED = 50_000;

    private static final class Window {
        long startedAt;
        int count;
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Limit limit = "POST".equalsIgnoreCase(request.getMethod()) ? LIMITS.get(request.getRequestURI()) : null;
        if (limit != null && !allowed(request.getRequestURI() + "|" + clientAddress(request), limit, System.currentTimeMillis())) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(limit.windowMillis() / 1000));
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"تلاش‌ها زیاد شد. چند دقیقهٔ دیگر دوباره امتحان کن.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    boolean allowed(String key, Limit limit, long now) {
        if (windows.size() > MAX_TRACKED) {
            windows.clear();
        }
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        synchronized (window) {
            if (now - window.startedAt >= limit.windowMillis()) {
                window.startedAt = now;
                window.count = 0;
            }
            window.count++;
            return window.count <= limit.max();
        }
    }

    /** Behind the proxies the first forwarded address is the visitor's; Caddy sets it and ignores what the visitor sends. */
    static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
