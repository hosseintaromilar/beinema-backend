package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OtpRequestResponse {

    private String message;

    private boolean signedIn;

    private String token;

    private boolean profileComplete;

    private String role;
}
