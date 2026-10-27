package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleSignInRequest {

    // the ID token from "Sign in with Google"
    private String credential;

    // the inviter's code from a referral link, if any
    private String referralCode;
}
