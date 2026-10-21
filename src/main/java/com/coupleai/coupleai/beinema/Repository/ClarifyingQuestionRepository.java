package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Enum.QuestionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClarifyingQuestionRepository extends JpaRepository<ClarifyingQuestion, Long> {

    Optional<ClarifyingQuestion> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ClarifyingQuestion q where q.publicId = :publicId")
    Optional<ClarifyingQuestion> findByPublicIdForUpdate(@Param("publicId") String publicId);

    List<ClarifyingQuestion> findAllByPublicIdIn(Collection<String> publicIds);

    List<ClarifyingQuestion> findAllByChatRoomIdOrderByIdAsc(Long chatRoomId);

    List<ClarifyingQuestion> findAllByChatRoomIdAndStatus(Long chatRoomId, QuestionStatus status);

    long countByChatRoomIdAndSessionNumberAndStatusIn(Long chatRoomId, Integer sessionNumber, Collection<QuestionStatus> statuses);

    void deleteAllByChatRoomId(Long chatRoomId);
}
