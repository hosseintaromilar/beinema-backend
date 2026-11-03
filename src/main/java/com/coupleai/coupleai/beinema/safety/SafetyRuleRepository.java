package com.coupleai.coupleai.beinema.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SafetyRuleRepository extends JpaRepository<SafetyRule, Long> {

    List<SafetyRule> findAllByEnabledTrue();

    List<SafetyRule> findAllByOrderByCategoryAscIdAsc();
}
