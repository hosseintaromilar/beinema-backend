package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.AgentAccessResponse;
import com.coupleai.coupleai.beinema.DTO.ChatRoom.PurchaseAgentAccessRequest;
import com.coupleai.coupleai.beinema.Service.AgentAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class AgentAccessController {

    private final AgentAccessService agentAccessService;

    @GetMapping("/{chatRoomId}/agent-access")
    public ResponseEntity<AgentAccessResponse> getActiveAccess(
            @PathVariable Long chatRoomId,
            Authentication authentication
    ) {
        AgentAccessResponse access = agentAccessService.getActiveAccess(
                authentication.getName(),
                chatRoomId
        );
        return ResponseEntity.ok(access);
    }

    @PostMapping("/{chatRoomId}/agent-access")
    public ResponseEntity<AgentAccessResponse> purchase(
            @PathVariable Long chatRoomId,
            Authentication authentication,
            @RequestBody PurchaseAgentAccessRequest request
    ) {
        return ResponseEntity.ok(
                agentAccessService.purchase(
                        authentication.getName(),
                        chatRoomId,
                        request
                )
        );
    }
}
