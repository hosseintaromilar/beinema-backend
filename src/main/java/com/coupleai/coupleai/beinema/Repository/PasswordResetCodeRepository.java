package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetCodeRepository
        extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(
            String email
    );
}
