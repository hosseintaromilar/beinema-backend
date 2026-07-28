package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository
        extends JpaRepository<ChatRoomMember, Long> {


    List<ChatRoomMember> findByUserId(Long userId);


    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);


    Optional<ChatRoomMember> findByChatRoomIdAndUserId(

            Long chatRoomId,

            Long userId

    );


    boolean existsByChatRoomIdAndUserId(

            Long chatRoomId,

            Long userId

    );

}