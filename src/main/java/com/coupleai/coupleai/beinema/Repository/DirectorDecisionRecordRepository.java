package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Enum.DecisionKind;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DirectorDecisionRecordRepository extends JpaRepository<DirectorDecisionRecord, Long> {

    Optional<DirectorDecisionRecord> findByChatRoomIdAndTriggerMessageId(Long chatRoomId, Long triggerMessageId);

    List<DirectorDecisionRecord> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Pageable pageable);

    List<DirectorDecisionRecord> findByChatRoomIdAndKindOrderByIdDesc(Long chatRoomId, DecisionKind kind, Pageable pageable);

    void deleteAllByChatRoomId(Long chatRoomId);
}
