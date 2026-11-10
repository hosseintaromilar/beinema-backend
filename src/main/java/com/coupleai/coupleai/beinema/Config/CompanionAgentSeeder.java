package com.coupleai.coupleai.beinema.Config;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import com.coupleai.coupleai.beinema.agents.AgentPacks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The companions of the packs (classpath:agents/<slug>.json, AgentPacks). Each is added
 * once, when no agent uses its workflow yet; after that the admin panel owns it (name,
 * photo, texts, active or not) and nothing here touches it. A companion that moved to a new
 * workflow (formerWorkflowIds) keeps its name, photo and texts.
 * Runs before AgentPlanSeeder so a new companion gets its access plans too.
 */
@Component
@Order(15)
public class CompanionAgentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanionAgentSeeder.class);

    private final AgentRepository agentRepository;
    private final String coupleWorkflowId;

    public CompanionAgentSeeder(AgentRepository agentRepository,
                                @Value("${metis.couple-workflow-id}") String coupleWorkflowId) {
        this.agentRepository = agentRepository;
        this.coupleWorkflowId = coupleWorkflowId;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Agent> agents = agentRepository.findAll();
        for (Agent agent : agents) {
            for (AgentPacks.Pack pack : AgentPacks.all()) {
                if (agent.getAiBotId() != null && pack.formerWorkflowIds().contains(agent.getAiBotId())) {
                    agent.setAiBotId(pack.workflowId());
                    agentRepository.save(agent);
                    log.info("Companion {} moved to its new workflow", agent.getName());
                }
            }
        }
        for (AgentPacks.Pack pack : AgentPacks.all()) {
            AgentType type = AgentType.valueOf(pack.type());
            if (type == AgentType.RELATIONSHIP) {
                // a new database starts without the couple counselor
                if (agents.stream().noneMatch(agent -> agent.getType() == AgentType.RELATIONSHIP)) {
                    agentRepository.save(newAgent(pack, type, coupleWorkflowId));
                    log.info("Couple counselor added");
                }
                continue;
            }
            if (agents.stream().anyMatch(agent -> pack.workflowId().equals(agent.getAiBotId()))) {
                continue;
            }
            agentRepository.save(newAgent(pack, type, pack.workflowId()));
            log.info("Companion added: {}", pack.seed().name());
        }
    }

    private static Agent newAgent(AgentPacks.Pack pack, AgentType type, String workflowId) {
        AgentPacks.Seed seed = pack.seed();
        return Agent.builder()
                .name(seed.name())
                .type(type)
                .headline(seed.headline())
                .description(seed.description())
                .bio(blankToNull(seed.bio()))
                .specialties(blankToNull(seed.specialties()))
                .photoUrl(seed.photo())
                .avatar(seed.photo())
                .aiBotId(workflowId)
                .status(AgentStatus.ACTIVE)
                .build();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
