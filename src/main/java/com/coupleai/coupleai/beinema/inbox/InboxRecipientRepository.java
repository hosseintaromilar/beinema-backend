package com.coupleai.coupleai.beinema.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InboxRecipientRepository extends JpaRepository<InboxRecipient, Long> {

    List<InboxRecipient> findAllByUserIdAndDeletedAtIsNullOrderByIdDesc(Long userId);

    Optional<InboxRecipient> findByMessageIdAndUserId(Long messageId, Long userId);

    long countByMessageIdAndReadAtIsNotNull(Long messageId);

    void deleteAllByUserId(Long userId);
}
