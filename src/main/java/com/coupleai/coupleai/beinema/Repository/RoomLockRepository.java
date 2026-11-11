package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.RoomLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomLockRepository extends JpaRepository<RoomLock, Long> {

    Optional<RoomLock> findByUserIdAndChatRoomId(Long userId, Long chatRoomId);

    boolean existsByUserIdAndChatRoomId(Long userId, Long chatRoomId);

    List<RoomLock> findAllByUserIdAndChatRoomIdIn(Long userId, Collection<Long> chatRoomIds);

    void deleteByChatRoomId(Long chatRoomId);
}
