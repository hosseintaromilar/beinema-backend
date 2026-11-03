package com.coupleai.coupleai.beinema.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiSessionPauseRepository extends JpaRepository<AiSessionPause, Long> {

    List<AiSessionPause> findAllByChatRoomIdAndSessionNumber(Long chatRoomId, Integer sessionNumber);

    void deleteAllByChatRoomId(Long chatRoomId);
}
