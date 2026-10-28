package com.coupleai.coupleai.beinema.DTO.User;

import lombok.Getter;
import lombok.Setter;

/** Setting or changing one's password; currentPassword only when one is already set. */
@Getter
@Setter
public class ChangePasswordRequest {

    private String currentPassword;

    private String newPassword;
}
