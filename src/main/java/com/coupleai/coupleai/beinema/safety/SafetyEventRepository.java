package com.coupleai.coupleai.beinema.safety;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyEventRepository extends JpaRepository<SafetyEvent, Long> {

    boolean existsByChatRoomIdAndUserIdAndCategoryAndSessionNumber(Long chatRoomId, Long userId, String category,
                                                                    Integer sessionNumber);

    void deleteAllByChatRoomId(Long chatRoomId);
}
