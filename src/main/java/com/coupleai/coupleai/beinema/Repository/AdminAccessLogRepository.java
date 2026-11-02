package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.AdminAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAccessLogRepository extends JpaRepository<AdminAccessLog, Long> {

    List<AdminAccessLog> findTop200ByOrderByIdDesc();
}
