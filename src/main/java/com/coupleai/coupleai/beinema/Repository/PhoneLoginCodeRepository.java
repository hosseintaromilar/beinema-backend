package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.PhoneLoginCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhoneLoginCodeRepository extends JpaRepository<PhoneLoginCode, Long> {

    Optional<PhoneLoginCode> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    Optional<PhoneLoginCode> findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(String phoneNumber);

    List<PhoneLoginCode> findByPhoneNumberAndUsedFalse(String phoneNumber);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, LocalDateTime createdAt);
}
