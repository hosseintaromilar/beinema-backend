package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.PersonMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonMilestoneRepository extends JpaRepository<PersonMilestone, Long> {

    List<PersonMilestone> findAllByChatRoomIdOrderByIdAsc(Long chatRoomId);

    List<PersonMilestone> findAllByChatRoomIdAndUserIdOrderByIdAsc(Long chatRoomId, Long userId);
}
