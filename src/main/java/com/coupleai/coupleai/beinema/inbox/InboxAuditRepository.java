package com.coupleai.coupleai.beinema.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboxAuditRepository extends JpaRepository<InboxAudit, Long> {

    List<InboxAudit> findTop200ByOrderByIdDesc();
}
