package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.SessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRecordRepository extends JpaRepository<SessionRecord, Long> {

    Optional<SessionRecord> findTopByChatRoomIdOrderByIdDesc(Long chatRoomId);

    long countByChatRoomIdAndCompletedTrue(Long chatRoomId);

    /** The sessions with a conversation in them that ended in a window (the nightly mirror). */
    List<SessionRecord> findAllByEndedAtGreaterThanAndEndedAtLessThanEqualAndCompletedTrue(Instant from, Instant to);

    List<SessionRecord> findAllByChatRoomIdAndOpenedAtAfterOrderByOpenedAtAsc(Long chatRoomId, Instant after);
}
