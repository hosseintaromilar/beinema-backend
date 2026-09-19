package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.RelationshipInsightResponse;
import com.coupleai.coupleai.beinema.Service.RelationshipInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class RelationshipInsightController {

    private final RelationshipInsightService relationshipInsightService;

    @GetMapping("/{chatRoomId}/insight")
    public ResponseEntity<RelationshipInsightResponse> getInsight(
            @PathVariable Long chatRoomId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                relationshipInsightService.getInsight(
                        authentication.getName(),
                        chatRoomId
                )
        );
    }

    @PostMapping("/{chatRoomId}/insight/refresh")
    public ResponseEntity<RelationshipInsightResponse> refreshInsight(
            @PathVariable Long chatRoomId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                relationshipInsightService.refreshInsight(
                        authentication.getName(),
                        chatRoomId
                )
        );
    }
}
