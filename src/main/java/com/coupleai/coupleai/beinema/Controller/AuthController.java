package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.LoginRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.LogoutResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.RegisterRequest;
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

}