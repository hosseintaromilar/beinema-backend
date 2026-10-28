package com.coupleai.coupleai.beinema.DTO.User;

import lombok.Getter;
import lombok.Setter;

/** The second step after signing up: the name, and the mobile number for an account that has none (Google). */
@Getter
@Setter
public class CompleteNameRequest {

    private String firstName;

    private String lastName;

}
