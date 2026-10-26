package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select participant from ChatRoomParticipant participant "
            + "where participant.chatRoom.id = :chatRoomId and participant.user.id = :userId")
    java.util.Optional<ChatRoomParticipant> findByChatRoomIdAndUserIdForUpdate(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct participant from ChatRoomParticipant participant "
            + "join fetch participant.user "
            + "where participant.chatRoom.id = :chatRoomId")
    List<ChatRoomParticipant> findAllByChatRoomIdForUpdate(
            @Param("chatRoomId") Long chatRoomId
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

    @Query("select distinct participant.chatRoom.id from ChatRoomParticipant participant "
            + "where participant.pausedUntil is not null and participant.pausedUntil <= :now")
    java.util.List<Long> findRoomIdsWithExpiredPause(
            @Param("now") java.time.Instant now
    );

}
