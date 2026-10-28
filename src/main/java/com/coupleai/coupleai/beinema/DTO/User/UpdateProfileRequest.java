package com.coupleai.coupleai.beinema.DTO.User;

import lombok.Getter;
import lombok.Setter;

/** Editing one's own profile: the name and the username. */
@Getter
@Setter
public class UpdateProfileRequest {

    private String firstName;

    private String lastName;

    private String username;
}
