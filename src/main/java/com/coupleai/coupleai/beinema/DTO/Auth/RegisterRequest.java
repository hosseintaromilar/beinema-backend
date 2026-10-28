package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.Getter;
import lombok.Setter;

/** Sign-up: a username and a password; the name is asked right after. */
@Getter
@Setter
public class RegisterRequest {

    private String username;

    private String password;

    /** From a referral link, if any. */
    private String referralCode;

}
