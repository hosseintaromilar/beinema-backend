package com.coupleai.coupleai.beinema.market;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarketReportRepository extends JpaRepository<MarketReport, Long> {

    Optional<MarketReport> findByPublicId(String publicId);

    @Query("select report from MarketReport report where report.userId = :userId or report.chatRoomId in :roomIds order by report.id desc")
    List<MarketReport> findVisible(@Param("userId") Long userId, @Param("roomIds") Collection<Long> roomIds);

    Optional<MarketReport> findTopByUserIdOrderByIdDesc(Long userId);

    boolean existsByChatRoomIdIn(Collection<Long> chatRoomIds);

    void deleteAllByChatRoomId(Long chatRoomId);
}
