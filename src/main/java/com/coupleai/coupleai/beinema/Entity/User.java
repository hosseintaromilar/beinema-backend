package com.coupleai.coupleai.beinema.Entity;


import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;


@Entity

@Table(

        name = "users",

        uniqueConstraints = {

                @UniqueConstraint(

                        name = "uk_users_email",

                        columnNames = "email"

                )

        }

)

@Getter

@Setter

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class User extends BaseEntity {


    @Column(

            nullable = false,

            length = 100

    )

    private String name;



    @Column(

            nullable = false,

            unique = true,

            length = 150

    )

    private String email;



    @Column(

            unique = true,

            length = 20

    )

    private String phoneNumber;


    /** The sign-in name (lower case); accounts made with a mobile number before it have none. */
    @Column(unique = true, length = 30)
    private String username;


    @Column(length = 50)
    private String firstName;


    @Column(length = 50)
    private String lastName;



    @Column(

            length = 500

    )

    private String avatarUrl;



    @Column(

            nullable = false

    )

    private String password;



    @Enumerated(EnumType.STRING)

    @Column(

            nullable = false,

            length = 30

    )

    @Builder.Default

    private UserRole role = UserRole.USER;



    @Enumerated(EnumType.STRING)

    @Column(

            nullable = false,

            length = 30

    )

    @Builder.Default

    private UserStatus status = UserStatus.ACTIVE;



    @Column(

            nullable = false

    )

    @Builder.Default

    private boolean emailVerified = false;



    private LocalDateTime lastLoginAt;



    @Column(

            nullable = false

    )

    @Builder.Default
    private boolean deleted = false;

    @Column(name = "referral_code", unique = true, length = 12)
    private String referralCode;

    // Google account id ("sub" claim) of a user who signs in with Google
    @Column(name = "google_subject", unique = true, length = 64)
    private String googleSubject;

    /**
     * Free sessions this person has used as the creator of a conversation
     * (SessionBillingService); null for accounts from before it, read as 0.
     */
    @Column(name = "free_sessions_used")
    private Integer freeSessionsUsed;

    /**
     * The number was confirmed with an SMS code. While sign-up is by password,
     * numbers are only written down (false), to be confirmed once SMS is back.
     */
    @Column(name = "phone_verified")
    private Boolean phoneVerified;

    /**
     * The person chose this password (sign-up by number + password). Accounts
     * made by SMS code or Google carry a random one: null here.
     */
    @Column(name = "password_set")
    private Boolean passwordSet;

    public boolean hasPhoneNumber() {
        return phoneNumber != null && !phoneNumber.isBlank();
    }

    public boolean hasRegisteredName() {
        return name != null && !name.isBlank();
    }


    public String visibleName() {
        if (hasRegisteredName()) {
            return name.trim();
        }
        return "کاربر";
    }


    public void updateLastLogin() {

        this.lastLoginAt = LocalDateTime.now();

    }



    public void deactivate() {

        this.status = UserStatus.INACTIVE;

    }



    public void activate() {

        this.status = UserStatus.ACTIVE;

    }



    public boolean isActive() {

        return this.status == UserStatus.ACTIVE

                && !this.deleted;

    }

}