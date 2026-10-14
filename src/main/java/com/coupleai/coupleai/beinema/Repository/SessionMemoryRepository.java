package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.SessionMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionMemoryRepository extends JpaRepository<SessionMemory, Long> {

    Optional<SessionMemory> findByChatRoomId(Long chatRoomId);
}
