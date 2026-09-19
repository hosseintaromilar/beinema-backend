package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.RelationshipInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RelationshipInsightRepository
        extends JpaRepository<RelationshipInsight, Long> {

    Optional<RelationshipInsight> findByChatRoom(ChatRoom chatRoom);

    void deleteAllByChatRoom(ChatRoom chatRoom);
}
