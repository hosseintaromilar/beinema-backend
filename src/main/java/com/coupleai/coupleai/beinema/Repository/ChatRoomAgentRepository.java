package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomAgentRepository
        extends JpaRepository<ChatRoomAgent, Long> {

    List<ChatRoomAgent> findAllByChatRoom_Id(Long chatRoomId);

    @Query("select distinct link from ChatRoomAgent link "
            + "join fetch link.agent "
            + "where link.chatRoom = :chatRoom")
    List<ChatRoomAgent> findAllByChatRoom(@Param("chatRoom") ChatRoom chatRoom);

    @Query("select agent.name from ChatRoomAgent link "
            + "join link.agent agent "
            + "where link.chatRoom.id = :chatRoomId "
            + "and link.active = true")
    List<String> findActiveAgentNames(@Param("chatRoomId") Long chatRoomId);

    @Query("select agent.id from ChatRoomAgent link "
            + "join link.agent agent "
            + "where link.chatRoom.id = :chatRoomId "
            + "and link.active = true")
    List<Long> findActiveAgentIds(@Param("chatRoomId") Long chatRoomId);

    void deleteAllByChatRoom(ChatRoom chatRoom);
}
