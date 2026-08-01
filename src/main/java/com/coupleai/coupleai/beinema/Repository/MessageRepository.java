package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChatRoomIdAndDeletedFalseOrderBySequenceNumberAsc(
            Long chatRoomId
    );

    @Query("""
       select coalesce(max(m.sequenceNumber),0)
       from Message m
       where m.chatRoom.id = :chatRoomId
       """)
    Integer findLastSequenceNumber(Long chatRoomId);


    void deleteAllByChatRoom(ChatRoom chatRoom);

}