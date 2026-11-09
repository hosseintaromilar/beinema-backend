package com.coupleai.coupleai.beinema.teach;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeachCardRepository extends JpaRepository<TeachCard, Long> {

    Optional<TeachCard> findByPublicId(String publicId);

    List<TeachCard> findAllByUserIdOrderByIdDesc(Long userId);

    List<TeachCard> findAllByChatRoomIdOrderByIdAsc(Long chatRoomId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
