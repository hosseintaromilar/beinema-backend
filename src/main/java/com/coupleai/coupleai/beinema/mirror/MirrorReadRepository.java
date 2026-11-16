package com.coupleai.coupleai.beinema.mirror;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MirrorReadRepository extends JpaRepository<MirrorRead, Long> {

    Optional<MirrorRead> findByReportIdAndUserId(Long reportId, Long userId);

    List<MirrorRead> findAllByReportIdIn(Collection<Long> reportIds);

    void deleteAllByReportIdIn(Collection<Long> reportIds);
}
