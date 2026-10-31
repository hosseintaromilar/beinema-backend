package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository
        extends JpaRepository<ChatRoom, Long> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ChatRoom r set r.agentDeliveredSeq = :seq where r.id = :id and (r.agentDeliveredSeq is null or r.agentDeliveredSeq < :seq)")
    void markCompanionDelivered(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("seq") int seq);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ChatRoom r set r.agentReadSeq = :seq where r.id = :id and (r.agentReadSeq is null or r.agentReadSeq < :seq)")
    void markCompanionRead(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("seq") int seq);

    /** For the admin dashboard. */
    long countByStatus(com.coupleai.coupleai.beinema.Enum.ChatRoomStatus status);

    long countByCreatedAtAfter(java.time.LocalDateTime since);
}
