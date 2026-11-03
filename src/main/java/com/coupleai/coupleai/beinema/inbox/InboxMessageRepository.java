package com.coupleai.coupleai.beinema.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

    List<InboxMessage> findTop100ByTypeNotOrderByIdDesc(String type);

    List<InboxMessage> findAllByStatusAndScheduledAtBefore(String status, Instant before);

    Optional<InboxMessage> findByIdempotencyKey(String idempotencyKey);

    long countBySenderIdIsNotNullAndAudienceKindNotAndCreatedAtAfter(String audienceKind, java.time.LocalDateTime after);

    long countBySenderIdAndAudienceKindAndCreatedAtAfter(Long senderId, String audienceKind, java.time.LocalDateTime after);
}
