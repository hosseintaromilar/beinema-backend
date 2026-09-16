package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {
    private String email;
    private String code;
    private String newPassword;
}
