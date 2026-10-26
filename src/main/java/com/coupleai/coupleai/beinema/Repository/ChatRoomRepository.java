package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository
        extends JpaRepository<ChatRoom, Long> {

    /** For the admin dashboard. */
    long countByStatus(com.coupleai.coupleai.beinema.Enum.ChatRoomStatus status);

    long countByCreatedAtAfter(java.time.LocalDateTime since);
}
