package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpPhoneRequest {

    private String phoneNumber;

    private String referralCode;
}
