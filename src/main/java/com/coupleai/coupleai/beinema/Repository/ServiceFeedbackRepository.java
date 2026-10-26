package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ServiceFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceFeedbackRepository extends JpaRepository<ServiceFeedback, Long> {

    Optional<ServiceFeedback> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    List<ServiceFeedback> findAllByOrderByIdDesc();
}
