package com.coupleai.coupleai.beinema.mirror;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MirrorReportRepository extends JpaRepository<MirrorReport, Long> {

    Optional<MirrorReport> findByChatRoomIdAndDay(Long chatRoomId, LocalDate day);

    List<MirrorReport> findTop30ByChatRoomIdAndStatusOrderByDayDesc(Long chatRoomId, String status);

    List<MirrorReport> findAllByChatRoomIdInAndStatusAndDayGreaterThanEqualOrderByDayDesc(
            Collection<Long> chatRoomIds, String status, LocalDate since);

    /** Written versions in a time span (the email for those not opened within a day). */
    List<MirrorReport> findAllByStatusAndBuiltAtBetween(String status, java.time.Instant from, java.time.Instant to);

    List<MirrorReport> findAllByChatRoomIdInAndStatusAndBuiltAtAfterOrderByBuiltAtDesc(
            Collection<Long> chatRoomIds, String status, java.time.Instant since);

    List<MirrorReport> findAllByChatRoomId(Long chatRoomId);

    void deleteAllByChatRoomId(Long chatRoomId);
}
