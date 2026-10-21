package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientIntentRepository extends JpaRepository<ClientIntent, Long> {

    List<ClientIntent> findAllByChatRoomIdOrderByIdAsc(Long chatRoomId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
