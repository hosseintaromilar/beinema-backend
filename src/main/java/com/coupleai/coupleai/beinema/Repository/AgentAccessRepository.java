package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.AgentAccess;
import com.coupleai.coupleai.beinema.Enum.AgentAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AgentAccessRepository extends JpaRepository<AgentAccess, Long> {

    Optional<AgentAccess> findFirstByChatRoom_IdAndAgent_IdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
            Long chatRoomId,
            Long agentId,
            AgentAccessStatus status,
            LocalDateTime now
    );

    boolean existsByChatRoom_IdAndAgent_IdAndStatusAndExpiresAtAfter(
            Long chatRoomId,
            Long agentId,
            AgentAccessStatus status,
            LocalDateTime now
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from AgentAccess access where access.chatRoom.id = :chatRoomId")
    void deleteByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
