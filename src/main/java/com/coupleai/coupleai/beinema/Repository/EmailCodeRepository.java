package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.EmailCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailCodeRepository extends JpaRepository<EmailCode, Long> {

    Optional<EmailCode> findFirstByEmailNormalizedAndPurposeAndConsumedAtIsNullOrderByIdDesc(String emailNormalized, String purpose);

    List<EmailCode> findAllByEmailNormalizedAndPurposeAndConsumedAtIsNull(String emailNormalized, String purpose);

    long countByEmailNormalizedAndCreatedAtAfter(String emailNormalized, java.time.LocalDateTime after);

    long countByIpAndCreatedAtAfter(String ip, java.time.LocalDateTime after);

    void deleteAllByExpiresAtBefore(Instant before);
}
