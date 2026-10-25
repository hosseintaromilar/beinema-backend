package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.ForgotPasswordRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.GoogleConfigResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.GoogleSignInRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.LoginRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.LogoutResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpPhoneRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpRequestResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.OtpVerifyRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.RegisterRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.ResetPasswordRequest;
import com.coupleai.coupleai.beinema.Service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(

            @RequestBody RegisterRequest request

    ) {

        return ResponseEntity.ok(

                authService.register(request)

        );

    }


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(

            @RequestBody LoginRequest request

    ) {

        return ResponseEntity.ok(

                authService.login(request)

        );

    }


    @PostMapping("/otp/request")
    public ResponseEntity<OtpRequestResponse> requestOtp(

            @RequestBody OtpPhoneRequest request

    ) {

        return ResponseEntity.ok(
                authService.requestOtp(request)
        );

    }


    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(

            @RequestBody OtpVerifyRequest request

    ) {

        return ResponseEntity.ok(
                authService.verifyOtp(request)
        );

    }


    @GetMapping("/google/config")
    public ResponseEntity<GoogleConfigResponse> googleConfig() {

        return ResponseEntity.ok(
                authService.googleConfig()
        );

    }


    @PostMapping("/google")
    public ResponseEntity<AuthResponse> signInWithGoogle(

            @RequestBody GoogleSignInRequest request

    ) {

        return ResponseEntity.ok(
                authService.signInWithGoogle(request)
        );

    }


    // after a Google sign-in: verify the phone number once
    @PostMapping("/phone/request")
    public ResponseEntity<OtpRequestResponse> requestPhoneLink(

            @RequestBody OtpPhoneRequest request

    ) {

        return ResponseEntity.ok(
                authService.requestPhoneLink(request)
        );

    }


    @PostMapping("/phone/verify")
    public ResponseEntity<AuthResponse> verifyPhoneLink(

            @RequestBody OtpVerifyRequest request

    ) {

        return ResponseEntity.ok(
                authService.verifyPhoneLink(request)
        );

    }


    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(

            HttpServletRequest request

    ) {

        return ResponseEntity.ok(

                authService.logout(

                        request.getHeader("Authorization")

                )

        );

    }


    @PostMapping("/forgot-password")
    public ResponseEntity<LogoutResponse> forgotPassword(

            @RequestBody ForgotPasswordRequest request

    ) {

        return ResponseEntity.ok(
                authService.forgotPassword(request)
        );

    }


    @PostMapping("/reset-password")
    public ResponseEntity<LogoutResponse> resetPassword(

            @RequestBody ResetPasswordRequest request

    ) {

        return ResponseEntity.ok(
                authService.resetPassword(request)
        );

    }

}