package com.coupleai.coupleai.beinema.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAlertRepository extends JpaRepository<AdminAlert, Long> {

    List<AdminAlert> findTop100ByOrderByIdDesc();

    long countBySeenFalse();
}
