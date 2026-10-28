package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.Getter;
import lombok.Setter;

/** Sign-in with the username (or, for older accounts, the mobile number) and the password. */
@Getter
@Setter
public class LoginRequest {

    private String username;

    /** Older clients sent the number here. */
    private String phoneNumber;

    private String password;

}
