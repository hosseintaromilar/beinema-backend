package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.CounselingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CounselingStateRepository extends JpaRepository<CounselingState, Long> {

    Optional<CounselingState> findByChatRoomId(Long chatRoomId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
