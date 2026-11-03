package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.SessionEndCheck;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionEndCheckRepository extends JpaRepository<SessionEndCheck, Long> {

    boolean existsByChatRoomIdAndSessionNumber(Long chatRoomId, int sessionNumber);

    boolean existsByChatRoomIdAndStatus(Long chatRoomId, String status);

    Optional<SessionEndCheck> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SessionEndCheck c where c.publicId = :publicId")
    Optional<SessionEndCheck> findByPublicIdForUpdate(@Param("publicId") String publicId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
