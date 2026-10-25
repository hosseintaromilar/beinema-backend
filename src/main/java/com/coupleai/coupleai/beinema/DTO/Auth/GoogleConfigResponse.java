package com.coupleai.coupleai.beinema.DTO.Auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GoogleConfigResponse {

    private boolean enabled;

    // public: the browser needs it to show the Google button
    private String clientId;
}
