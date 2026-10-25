package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private String message;

    private String token;

    private boolean profileComplete;

    private String role;

    // signed in with Google and the phone number is not verified yet
    private boolean phoneRequired;

}