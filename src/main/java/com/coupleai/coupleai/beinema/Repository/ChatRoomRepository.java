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

    /** Rooms with a session running (sessionActive is null while one runs). */
    @org.springframework.data.jpa.repository.Query("select r from ChatRoom r where r.status = :status "
            + "and r.sessionStartedAt is not null and (r.sessionActive is null or r.sessionActive = true)")
    java.util.List<ChatRoom> findRunning(@org.springframework.data.repository.query.Param("status") com.coupleai.coupleai.beinema.Enum.ChatRoomStatus status);

    /** How many sessions of this owner run now and are not settled yet, other rooms only. */
    @org.springframework.data.jpa.repository.Query("select count(r) from ChatRoom r where r.createdBy = :owner and r.id <> :room "
            + "and r.status = com.coupleai.coupleai.beinema.Enum.ChatRoomStatus.ACTIVE "
            + "and r.sessionStartedAt is not null and (r.sessionActive is null or r.sessionActive = true)")
    long countRunningOf(@org.springframework.data.repository.query.Param("owner") Long owner,
                        @org.springframework.data.repository.query.Param("room") Long room);
}
