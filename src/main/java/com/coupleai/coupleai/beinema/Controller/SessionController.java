package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.SessionInfo;
import com.coupleai.coupleai.beinema.Service.SessionLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Clock, break and entry actions of a counseling room. */
@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionLifecycleService sessionLifecycleService;

    /** A countdown on a phone reached a limit; the server decides what follows (the break offer, the end). */
    @PostMapping("/time-up")
    public SessionInfo timeUp(@PathVariable Long chatRoomId) {
        return sessionLifecycleService.timeUp(chatRoomId);
    }

    /** Body: {"agree": true | false}. True: take the break; false: go on for the extra minutes. */
    @PostMapping("/break-vote")
    public SessionInfo breakVote(
            @PathVariable Long chatRoomId,
            @RequestBody Map<String, Object> body
    ) {
        boolean agree = body != null && Boolean.TRUE.equals(body.get("agree"));
        return sessionLifecycleService.vote(chatRoomId, agree);
    }

    /** The current user's own session summaries in this room (never the partner's). */
    @GetMapping("/my-summaries")
    public java.util.List<Map<String, Object>> mySummaries(@PathVariable Long chatRoomId) {
        return sessionLifecycleService.mySummaries(chatRoomId).stream()
                .map(item -> Map.<String, Object>of(
                        "sessionNumber", item.getSessionNumber(),
                        "summary", item.getSummary(),
                        "createdAt", item.getCreatedAt() == null
                                ? 0L
                                : item.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
                .toList();
    }

    /** The start button: opens the next session, or brings this partner into the open one. */
    @PostMapping({"/enter", "/start-next"})
    public SessionInfo enter(@PathVariable Long chatRoomId) {
        return sessionLifecycleService.enterSession(chatRoomId);
    }
}
