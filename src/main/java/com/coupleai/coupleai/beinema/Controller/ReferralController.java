package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Referral.ReferralPreviewResponse;
import com.coupleai.coupleai.beinema.DTO.Referral.ReferralProfileResponse;
import com.coupleai.coupleai.beinema.Service.ReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/me")
    public ResponseEntity<ReferralProfileResponse> me(Authentication authentication) {
        return ResponseEntity.ok(referralService.profile(authentication.getName()));
    }

    @GetMapping("/preview/{code}")
    public ResponseEntity<ReferralPreviewResponse> preview(@PathVariable String code) {
        return ResponseEntity.ok(referralService.preview(code));
    }
}
