package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomInvitation;
import com.coupleai.coupleai.beinema.Enum.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomInvitationRepository
        extends JpaRepository<ChatRoomInvitation, Long> {

    @Query("""
            select invitation
            from ChatRoomInvitation invitation
            join fetch invitation.chatRoom
            join fetch invitation.invitedBy
            left join fetch invitation.acceptedBy
            where invitation.token = :token
            """)
    Optional<ChatRoomInvitation> findByToken(@Param("token") String token);

    Optional<ChatRoomInvitation> findFirstByChatRoomAndPhoneNumberAndStatus(
            ChatRoom chatRoom,
            String phoneNumber,
            InvitationStatus status
    );

    void deleteAllByChatRoom(ChatRoom chatRoom);
}
