package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.SessionSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionSummaryRepository extends JpaRepository<SessionSummary, Long> {

    Optional<SessionSummary> findTopByChatRoomIdOrderBySessionNumberDesc(Long chatRoomId);

    boolean existsByChatRoomId(Long chatRoomId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
