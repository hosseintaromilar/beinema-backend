package com.coupleai.coupleai.beinema.DTO.User;

import com.coupleai.coupleai.beinema.Entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

    private Long id;

    private String name;

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private boolean profileComplete;

    // signed in with Google, phone number not verified yet
    private boolean phoneRequired;

    private String avatarUrl;

    private String role;

    private String status;

    private boolean emailVerified;

    /** They chose a password; changing it asks for the current one. */
    private boolean passwordSet;

    /** Signs in with Google too. */
    private boolean googleLinked;


    public static UserResponse from(User user) {

        return UserResponse.builder()

                .id(user.getId())

                .name(user.hasRegisteredName() ? user.getName().trim() : null)

                .firstName(user.getFirstName())

                .lastName(user.getLastName())

                .email(hiddenPhoneEmail(user.getEmail()) ? null : user.getEmail())

                .phoneNumber(user.getPhoneNumber())

                // the name and a mobile number (written down, not confirmed while SMS is off)
                .profileComplete(user.hasRegisteredName() && user.hasPhoneNumber())

                // a Google account without a number gives it in the same step as the name
                .phoneRequired(!user.hasPhoneNumber())

                .avatarUrl(user.getAvatarUrl())

                .role(user.getRole().name())

                .status(user.getStatus().name())

                .emailVerified(user.isEmailVerified())

                .passwordSet(Boolean.TRUE.equals(user.getPasswordSet()))

                .googleLinked(user.getGoogleSubject() != null)

                .build();

    }


    private static boolean hiddenPhoneEmail(String email) {
        return email != null && email.endsWith("@users.beinema");
    }

}