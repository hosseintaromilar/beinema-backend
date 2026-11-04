package com.coupleai.coupleai.beinema.mail;

import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Email sign-up, its code, forgotten passwords, and adding an email to an existing account. */
@RestController
@RequiredArgsConstructor
public class EmailAuthController {

    private final EmailAuthService emailAuthService;
    private final CurrentUserService currentUserService;

    /** Whether email is offered at all (the mail server is set). */
    @GetMapping("/api/auth/email/config")
    public Map<String, Boolean> config() {
        return Map.of("enabled", emailAuthService.available());
    }

    /** Body: email, password, displayName, referralCode (optional). */
    /** Body: email. Sends a sign-in code (also for a new address). */
    @PostMapping("/api/auth/email/start")
    public EmailAuthService.Result start(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return emailAuthService.start(body.get("email"), address(request));
    }

    /** Body: email, code. Signed in, or NEEDS_NAME with a ticket for /complete. */
    @PostMapping("/api/auth/email/verify")
    public EmailAuthService.LoginResult verifyLogin(@RequestBody Map<String, String> body) {
        return emailAuthService.verifyLogin(body.get("email"), body.get("code"));
    }

    /** Body: ticket, firstName, lastName, referralCode. Makes the account of a new address. */
    @PostMapping("/api/auth/email/complete")
    public AuthResponse complete(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return emailAuthService.complete(body.get("ticket"), body.get("firstName"), body.get("lastName"),
                body.get("referralCode"), address(request));
    }

    @PostMapping("/api/auth/signup")
    public EmailAuthService.Result signup(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return emailAuthService.signup(body.get("email"), body.get("password"), body.get("displayName"),
                body.get("referralCode"), address(request));
    }

    /** Body: email, code. */
    @PostMapping("/api/auth/verify-email")
    public AuthResponse verify(@RequestBody Map<String, String> body) {
        return emailAuthService.verifySignup(body.get("email"), body.get("code"));
    }

    /** Body: email, purpose (signup_verify or password_reset). */
    @PostMapping("/api/auth/resend-code")
    public EmailAuthService.Result resend(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return emailAuthService.resend(body.get("email"), body.get("purpose"), address(request));
    }

    /** Body: email. Always the same answer. */
    @PostMapping("/api/auth/email/forgot-password")
    public EmailAuthService.Result forgot(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return emailAuthService.forgot(body.get("email"), address(request));
    }

    /** Body: email, code, newPassword. */
    @PostMapping("/api/auth/email/reset-password")
    public AuthResponse reset(@RequestBody Map<String, String> body) {
        return emailAuthService.reset(body.get("email"), body.get("code"), body.get("newPassword"));
    }

    /** Signed in. Body: email, currentPassword (only to change a confirmed one). */
    @PostMapping("/api/account/add-email")
    public EmailAuthService.Result addEmail(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return emailAuthService.addEmail(currentUserService.getCurrentUser(), body.get("email"),
                body.get("currentPassword"), address(request));
    }

    /** Signed in. Body: email, code. */
    @PostMapping("/api/account/verify-email")
    public EmailAuthService.Result verifyAdded(@RequestBody Map<String, String> body) {
        return emailAuthService.verifyAddedEmail(currentUserService.getCurrentUser(), body.get("email"), body.get("code"));
    }

    private static String address(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
