package com.coupleai.coupleai.beinema.Config;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.AgentPlan;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Repository.AgentPlanRepository;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(20)
public class AgentPlanSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentPlanSeeder.class);

    private final AgentRepository agentRepository;
    private final AgentPlanRepository agentPlanRepository;

    @Value("${beinema.agent-plan.price.7:100000}")
    private long price7;

    @Value("${beinema.agent-plan.price.30:250000}")
    private long price30;

    @Value("${beinema.agent-plan.price.90:500000}")
    private long price90;

    public AgentPlanSeeder(
            AgentRepository agentRepository,
            AgentPlanRepository agentPlanRepository
    ) {
        this.agentRepository = agentRepository;
        this.agentPlanRepository = agentPlanRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Agent> agents = agentRepository.findByStatus(AgentStatus.ACTIVE);
        for (Agent agent : agents) {
            seedPlan(agent, 7, "دسترسی ۷ روزه", price7);
            seedPlan(agent, 30, "دسترسی ۳۰ روزه", price30);
            seedPlan(agent, 90, "دسترسی ۹۰ روزه", price90);
        }
        log.info("Default agent access plans are ready");
    }

    private void seedPlan(Agent agent, int days, String title, long price) {
        if (agentPlanRepository.existsByAgentAndDurationDays(agent, days)) {
            return;
        }
        agentPlanRepository.save(
                AgentPlan.builder()
                        .agent(agent)
                        .title(title)
                        .durationDays(days)
                        .price(price)
                        .active(true)
                        .build()
        );
    }
}
