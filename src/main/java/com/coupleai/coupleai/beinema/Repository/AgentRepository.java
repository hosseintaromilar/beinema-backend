package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository

        extends JpaRepository<Agent, Long> {
    List<Agent> findByStatus(AgentStatus status);

}