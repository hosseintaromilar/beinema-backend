package com.coupleai.coupleai.beinema.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetEntryRepository extends JpaRepository<BudgetEntry, Long> {

    List<BudgetEntry> findAllByChatRoomIdAndDayGreaterThanEqualOrderByDayDescIdDesc(Long chatRoomId, LocalDate from);

    Optional<BudgetEntry> findByPublicId(String publicId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
