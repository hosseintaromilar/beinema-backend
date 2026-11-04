package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A six-digit email code, kept only as a hash; one purpose, ten minutes, five tries. */
@Entity
@Table(name = "email_codes", indexes = @Index(name = "idx_email_code_address", columnList = "email_normalized,purpose"))
@Getter
@Setter
@NoArgsConstructor
public class EmailCode extends BaseEntity {

    public static final String SIGNUP_VERIFY = "signup_verify";
    public static final String PASSWORD_RESET = "password_reset";
    public static final String EMAIL_CHANGE = "email_change";
    public static final String MIGRATION_VERIFY = "migration_verify";
    /** Signing in or up with only the address: the code is the whole proof. */
    public static final String EMAIL_LOGIN = "email_login";

    @Column(name = "email_normalized", length = 150, nullable = false)
    private String emailNormalized;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 20, nullable = false)
    private String purpose;

    @Column(name = "code_hash", length = 64, nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(length = 64)
    private String ip;
}
