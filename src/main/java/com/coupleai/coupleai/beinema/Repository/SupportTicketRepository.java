package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.SupportTicket;
import com.coupleai.coupleai.beinema.Enum.SupportTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findAllByUserIdOrderByLastActivityAtDesc(Long userId);

    List<SupportTicket> findAllByStatusOrderByLastActivityAtDesc(SupportTicketStatus status);

    List<SupportTicket> findTop200ByOrderByLastActivityAtDesc();

    long countByStatus(SupportTicketStatus status);

    long countByUserIdAndStatusNot(Long userId, SupportTicketStatus status);

    long countByUserIdAndUnreadByUserTrue(Long userId);
}
