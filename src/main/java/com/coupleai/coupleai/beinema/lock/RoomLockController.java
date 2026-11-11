package com.coupleai.coupleai.beinema.lock;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

/** A person's own lock on a conversation: put on, change, take off, open at the door, and forgotten. */
@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}/lock")
@RequiredArgsConstructor
public class RoomLockController {

    public static final String KEY_HEADER = "X-Room-Key";

    private final RoomLockService lockService;
    private final CurrentUserService currentUserService;
    private final JwtService jwtService;

    public record PinRequest(String pin, String currentPin) {
    }

    @GetMapping
    public RoomLockService.Status status(@PathVariable Long chatRoomId, HttpServletRequest request) {
        return lockService.status(me(), chatRoomId, request.getHeader(KEY_HEADER), signedInAt(request));
    }

    @PostMapping
    public Map<String, String> set(@PathVariable Long chatRoomId, @RequestBody PinRequest body, HttpServletRequest request) {
        return Map.of("key", lockService.set(me(), chatRoomId, body.pin(), body.currentPin(), request.getHeader(KEY_HEADER)));
    }

    @PostMapping("/remove")
    public Map<String, Boolean> remove(@PathVariable Long chatRoomId, @RequestBody(required = false) PinRequest body,
                                       HttpServletRequest request) {
        lockService.remove(me(), chatRoomId, body == null ? null : body.pin(), request.getHeader(KEY_HEADER));
        return Map.of("locked", false);
    }

    @PostMapping("/open")
    public Map<String, String> open(@PathVariable Long chatRoomId, @RequestBody PinRequest body) {
        return Map.of("key", lockService.unlock(me(), chatRoomId, body.pin()));
    }

    @PostMapping("/forgot")
    public Map<String, Boolean> forgot(@PathVariable Long chatRoomId) {
        lockService.requestReset(me(), chatRoomId);
        return Map.of("signOut", true);
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@PathVariable Long chatRoomId, HttpServletRequest request) {
        return Map.of("key", lockService.reset(me(), chatRoomId, signedInAt(request)));
    }

    private Long me() {
        return currentUserService.getCurrentUser().getId();
    }

    private Instant signedInAt(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            Date issued = jwtService.extractIssuedAt(header.substring(7));
            return issued == null ? null : issued.toInstant();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
