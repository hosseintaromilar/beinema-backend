package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomParticipantRepository
        extends JpaRepository<ChatRoomParticipant, Long> {
    List<ChatRoomParticipant> findAllByUser(User user);
}