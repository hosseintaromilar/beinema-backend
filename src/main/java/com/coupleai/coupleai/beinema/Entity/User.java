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