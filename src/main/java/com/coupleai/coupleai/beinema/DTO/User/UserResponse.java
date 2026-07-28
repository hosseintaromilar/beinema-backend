package com.coupleai.coupleai.beinema.DTO.User;

import com.coupleai.coupleai.beinema.Entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

    private Long id;

    private String name;

    private String email;

    private String role;

    private String status;

    private boolean emailVerified;


    public static UserResponse from(User user) {

        return UserResponse.builder()

                .id(user.getId())

                .name(user.getName())

                .email(user.getEmail())

                .role(user.getRole().name())

                .status(user.getStatus().name())

                .emailVerified(user.isEmailVerified())

                .build();

    }

}