package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.CoupleTest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CoupleTestRepository extends JpaRepository<CoupleTest, Long> {

    List<CoupleTest> findAllByChatRoomIdOrderByIdAsc(Long chatRoomId);

    Optional<CoupleTest> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CoupleTest t where t.publicId = :publicId")
    Optional<CoupleTest> findByPublicIdForUpdate(@Param("publicId") String publicId);
}
