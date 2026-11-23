package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

    List<MessageAttachment> findAllByMessageId(Long messageId);

    List<MessageAttachment> findAllByChatRoomIdAndMessageIdIsNotNull(Long chatRoomId);

    Optional<MessageAttachment> findByIdAndChatRoomId(Long id, Long chatRoomId);

    List<MessageAttachment> findAllByIdIn(Collection<Long> ids);

    List<MessageAttachment> findAllByChatRoomId(Long chatRoomId);

    @Modifying
    @Query("update MessageAttachment a set a.messageId = :messageId where a.id = :id and a.messageId is null")
    int linkUnclaimed(@Param("id") Long id, @Param("messageId") Long messageId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update MessageAttachment a set a.summary = :summary, a.summaryStatus = :status where a.id = :id")
    void updateSummary(@Param("id") Long id, @Param("summary") String summary, @Param("status") String status);

    @Query("select coalesce(sum(a.sizeBytes), 0) from MessageAttachment a where a.chatRoomId = :roomId")
    long totalBytesInRoom(@Param("roomId") Long roomId);

    @Modifying
    @Query("delete from MessageAttachment a where a.chatRoomId = :roomId")
    void deleteByChatRoomId(@Param("roomId") Long roomId);
}
