package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomParticipantRepository
        extends JpaRepository<ChatRoomParticipant, Long> {
    List<ChatRoomParticipant> findAllByUser(User user);

    @Query("select distinct participant from ChatRoomParticipant participant "
            + "join fetch participant.chatRoom "
            + "join fetch participant.user "
            + "where participant.user.id = :userId")
    List<ChatRoomParticipant> findAllWithRoomByUserId(
            @Param("userId") Long userId
    );

    @Query("select distinct participant from ChatRoomParticipant participant "
            + "join fetch participant.user "
            + "where participant.chatRoom = :chatRoom")
    List<ChatRoomParticipant> findAllByChatRoom(
            @Param("chatRoom") ChatRoom chatRoom
    );

    long countByChatRoom(ChatRoom chatRoom);

    java.util.Optional<ChatRoomParticipant> findByChatRoomAndUser(
            ChatRoom chatRoom,
            User user
    );
    boolean existsByChatRoomAndUserAndRole(
            ChatRoom chatRoom,
            User user,
            ParticipantRole role
    );

    void deleteAllByChatRoom(ChatRoom chatRoom);

    void deleteByChatRoomAndUser(ChatRoom chatRoom, User user);

    boolean existsByChatRoomAndUser(
            ChatRoom chatRoom,
            User user
    );
}