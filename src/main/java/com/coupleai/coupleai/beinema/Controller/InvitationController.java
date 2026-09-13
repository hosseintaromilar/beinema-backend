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

    @PostMapping("/api/invitations/{token}/accept")
    public ResponseEntity<AcceptInvitationResponse> accept(
            @PathVariable String token
    ) {
        return ResponseEntity.ok(invitationService.accept(token));
    }
}
