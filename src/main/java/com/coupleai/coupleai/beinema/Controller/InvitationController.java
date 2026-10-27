package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Invitation.AcceptInvitationResponse;
import com.coupleai.coupleai.beinema.DTO.Invitation.InvitationPreviewResponse;
import com.coupleai.coupleai.beinema.DTO.Invitation.InvitePartnerRequest;
import com.coupleai.coupleai.beinema.DTO.Invitation.InvitePartnerResponse;
import com.coupleai.coupleai.beinema.Service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    /** The invitation link to share with the partner through any app (no SMS for now). */
    @PostMapping("/api/chat-rooms/{chatRoomId}/invitations/link")
    public InvitationService.InviteLink inviteLink(@PathVariable Long chatRoomId) {
        return invitationService.inviteLink(chatRoomId);
    }

    @PostMapping("/api/chat-rooms/{chatRoomId}/invitations")
    public ResponseEntity<InvitePartnerResponse> invitePartner(
            @PathVariable Long chatRoomId,
            @RequestBody InvitePartnerRequest request
    ) {
        return ResponseEntity.ok(
                invitationService.invitePartner(
                        chatRoomId,
                        request.getPhone()
                )
        );
    }

    @GetMapping("/api/invitations/{token}")
    public ResponseEntity<InvitationPreviewResponse> preview(
            @PathVariable String token
    ) {
        return ResponseEntity.ok(invitationService.preview(token));
    }

    /** Body: { "rulesAccepted": true } from the checkbox on the invitation page. */
    @PostMapping("/api/invitations/{token}/accept")
    public ResponseEntity<AcceptInvitationResponse> accept(
            @PathVariable String token,
            @RequestBody(required = false) java.util.Map<String, Object> body
    ) {
        boolean rulesAccepted = body != null && Boolean.TRUE.equals(body.get("rulesAccepted"));
        return ResponseEntity.ok(invitationService.accept(token, rulesAccepted));
    }
}
