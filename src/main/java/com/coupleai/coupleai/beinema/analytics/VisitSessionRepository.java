package com.coupleai.coupleai.beinema.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VisitSessionRepository extends JpaRepository<VisitSession, Long> {

    Optional<VisitSession> findBySid(String sid);

    boolean existsByVisitorId(String visitorId);

    List<VisitSession> findTop20ByOrderByStartedAtDesc();

    @Modifying
    @Query("update VisitSession s set s.pageviews = s.pageviews + 1, s.lastSeenAt = :at, s.exitPath = :path where s.id = :id")
    int viewed(@Param("id") Long id, @Param("at") LocalDateTime at, @Param("path") String path);

    @Modifying
    @Query("update VisitSession s set s.lastSeenAt = :at where s.id = :id and s.lastSeenAt < :at")
    int seen(@Param("id") Long id, @Param("at") LocalDateTime at);
}
