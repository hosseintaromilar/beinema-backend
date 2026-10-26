package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    List<Message> findAllByChatRoomOrderBySequenceNumberAsc(
            ChatRoom chatRoom
    );

    @Query("""
        SELECT MAX(m.sequenceNumber)
        FROM Message m
        WHERE m.chatRoom = :chatRoom
    """)
    Optional<Integer> findMaxSequenceNumberByChatRoom(
            ChatRoom chatRoom
    );

    Optional<Message> findFirstByChatRoomAndDeletedFalseOrderBySequenceNumberDesc(
            ChatRoom chatRoom
    );

    /** When someone of this kind last wrote in the room after the given message number. */
    @Query("""
            select max(m.createdAt)
            from Message m
            where m.chatRoom.id = :chatRoomId
              and m.senderType = :senderType
              and m.sequenceNumber > :afterSequence
            """)
    Optional<java.time.LocalDateTime> findLastMessageAtAfter(
            @Param("chatRoomId") Long chatRoomId,
            @Param("senderType") MessageSenderType senderType,
            @Param("afterSequence") int afterSequence
    );

    @Query("""
            select m.chatRoom.id, max(m.createdAt)
            from Message m
            where m.chatRoom.id in :roomIds
              and m.senderType = :senderType
              and m.senderId = :userId
            group by m.chatRoom.id
            """)
    List<Object[]> findLastUserMessageAtByRoomIds(
            @Param("roomIds") Collection<Long> roomIds,
            @Param("userId") Long userId,
            @Param("senderType") MessageSenderType senderType
    );

    /** For the admin dashboard: what people wrote since a moment. */
    long countBySenderTypeAndCreatedAtAfter(MessageSenderType senderType, java.time.LocalDateTime since);
}
