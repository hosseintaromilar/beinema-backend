package com.coupleai.coupleai.beinema.lock;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The door of a locked conversation: everything under /api/chat-rooms/{id} needs the person's key,
 * except the door itself (the lock endpoints). Without it the answer is 423 and nothing of the room.
 * The key comes in a header, or (for the live event stream) as a query parameter.
 */
@Component
@RequiredArgsConstructor
public class RoomLockInterceptor implements HandlerInterceptor {

    static final Pattern ROOM = Pattern.compile("^/api/chat-rooms/(\\d+)(/.*)?$");

    private final RoomLockService lockService;
    private final CurrentUserService currentUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Matcher matcher = ROOM.matcher(request.getRequestURI());
        if (!matcher.matches()) {
            return true;
        }
        String rest = matcher.group(2) == null ? "" : matcher.group(2);
        if (rest.equals("/lock") || rest.startsWith("/lock/")) {
            return true;
        }
        Long userId;
        try {
            userId = currentUserService.getCurrentUser().getId();
        } catch (RuntimeException signedOut) {
            return true; // security answers this request on its own
        }
        Long roomId = Long.valueOf(matcher.group(1));
        if (!lockService.isLocked(userId, roomId)) {
            return true;
        }
        String key = request.getHeader(RoomLockController.KEY_HEADER);
        if (key == null || key.isBlank()) {
            key = request.getParameter("roomKey");
        }
        if (lockService.opens(key, userId, roomId)) {
            return true;
        }
        response.setStatus(423);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"locked\":true,\"message\":\"این گفتگو قفل است.\"}");
        return false;
    }
}
