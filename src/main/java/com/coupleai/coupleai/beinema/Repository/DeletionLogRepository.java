package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.DeletionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeletionLogRepository extends JpaRepository<DeletionLog, Long> {

    List<DeletionLog> findTop200ByOrderByIdDesc();
}
