package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    boolean existsByReferredUserId(Long referredUserId);

    long countByReferrerUserId(Long referrerUserId);

    long countByReferrerUserIdAndReferrerAmountGreaterThanAndCreatedAtAfter(
            Long referrerUserId,
            Long amount,
            LocalDateTime createdAt
    );

    List<Referral> findTop10ByReferrerUserIdOrderByCreatedAtDesc(Long referrerUserId);

    @Query("select coalesce(sum(referral.referrerAmount), 0) from Referral referral "
            + "where referral.referrerUserId = :userId")
    long sumReferrerAmount(@Param("userId") Long userId);
}
