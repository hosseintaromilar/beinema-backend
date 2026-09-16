package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.AgentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentPlanRepository extends JpaRepository<AgentPlan, Long> {

    List<AgentPlan> findByAgentAndActiveTrueOrderByDurationDaysAsc(Agent agent);

    List<AgentPlan> findByAgentInAndActiveTrue(List<Agent> agents);

    boolean existsByAgentAndDurationDays(Agent agent, Integer durationDays);
}
