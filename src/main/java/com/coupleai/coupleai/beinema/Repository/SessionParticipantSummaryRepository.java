package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.SessionParticipantSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionParticipantSummaryRepository extends JpaRepository<SessionParticipantSummary, Long> {

    List<SessionParticipantSummary> findAllByChatRoomIdAndUserIdOrderBySessionNumberAsc(Long chatRoomId, Long userId);

    List<SessionParticipantSummary> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByChatRoomIdAndSessionNumberAndUserId(Long chatRoomId, Integer sessionNumber, Long userId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
