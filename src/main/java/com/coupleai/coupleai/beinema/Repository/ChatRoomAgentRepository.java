package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomAgentRepository
        extends JpaRepository<ChatRoomAgent, Long> {

    List<ChatRoomAgent> findAllByChatRoom(ChatRoom chatRoom);
    void deleteAllByChatRoom(ChatRoom chatRoom);
}