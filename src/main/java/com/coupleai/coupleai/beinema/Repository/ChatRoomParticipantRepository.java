package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomParticipantRepository
        extends JpaRepository<ChatRoomParticipant, Long> {
    List<ChatRoomParticipant> findAllByUser(User user);
    boolean existsByChatRoomAndUserAndRole(
            ChatRoom chatRoom,
            User user,
            ParticipantRole role
    );

    void deleteAllByChatRoom(ChatRoom chatRoom);

    boolean existsByChatRoomAndUser(
            ChatRoom chatRoom,
            User user
    );
}