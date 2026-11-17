package com.coupleai.coupleai.beinema.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetLimitRepository extends JpaRepository<BudgetLimit, Long> {

    List<BudgetLimit> findAllByChatRoomId(Long chatRoomId);

    Optional<BudgetLimit> findByChatRoomIdAndCategory(Long chatRoomId, String category);

    void deleteAllByChatRoomId(Long chatRoomId);
}
