package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.TopUpReceipt;
import com.coupleai.coupleai.beinema.Enum.TopUpReceiptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopUpReceiptRepository extends JpaRepository<TopUpReceipt, Long> {

    List<TopUpReceipt> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);

    List<TopUpReceipt> findAllByStatusOrderByCreatedAtAsc(TopUpReceiptStatus status);

    List<TopUpReceipt> findTop200ByOrderByCreatedAtDesc();

    long countByStatus(TopUpReceiptStatus status);

    long countByUserIdAndStatus(Long userId, TopUpReceiptStatus status);

    /** Locked, so two admins cannot approve the same receipt twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TopUpReceipt r where r.id = :id")
    Optional<TopUpReceipt> findByIdForUpdate(@Param("id") Long id);
}
