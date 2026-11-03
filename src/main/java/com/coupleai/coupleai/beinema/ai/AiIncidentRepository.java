package com.coupleai.coupleai.beinema.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiIncidentRepository extends JpaRepository<AiIncident, Long> {

    List<AiIncident> findTop50ByOrderByStartedAtDesc();
}
