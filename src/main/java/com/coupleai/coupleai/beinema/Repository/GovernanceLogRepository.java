package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.GovernanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GovernanceLogRepository extends JpaRepository<GovernanceLog, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from GovernanceLog log where log.chatRoomId = :chatRoomId")
    void deleteByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
