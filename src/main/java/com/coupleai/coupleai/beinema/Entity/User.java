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