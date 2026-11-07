package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.FinanceCard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FinanceCardRepository extends JpaRepository<FinanceCard, Long> {

    List<FinanceCard> findAllByChatRoomIdOrderByIdAsc(Long chatRoomId);

    Optional<FinanceCard> findFirstByChatRoomIdAndKindOrderByIdDesc(Long chatRoomId, String kind);

    Optional<FinanceCard> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FinanceCard c where c.publicId = :publicId")
    Optional<FinanceCard> findByPublicIdForUpdate(@Param("publicId") String publicId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
