package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An email address whose free session was used; never removed, also when the account is. */
@Entity
@Table(name = "free_session_grants")
@Getter
@Setter
@NoArgsConstructor
public class FreeSessionGrant extends BaseEntity {

    @Column(name = "email_normalized", length = 150, nullable = false, unique = true)
    private String emailNormalized;

    @Column(name = "user_id")
    private Long userId;
}
