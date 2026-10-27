package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.Getter;
import lombok.Setter;

/** Sign-in with the mobile number and the password. */
@Getter
@Setter
public class LoginRequest {

    private String phoneNumber;

    private String password;

}
