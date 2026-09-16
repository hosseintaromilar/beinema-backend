package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Agent.AgentRequest;
import com.coupleai.coupleai.beinema.DTO.Agent.AgentResponse;
import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.AgentPlan;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Repository.AgentPlanRepository;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {


    private final AgentRepository agentRepository;
    private final AgentPlanRepository agentPlanRepository;

    @Value("${beinema.agent-plan.default-duration-days:30}")
    private int defaultDurationDays;


    @Transactional(readOnly = true)
    public List<AgentResponse> getActiveAgents() {

        List<Agent> agents = agentRepository.findByStatus(AgentStatus.ACTIVE);
        if (agents.isEmpty()) {
            return List.of();
        }

        Map<Long, List<AgentPlan>> plansByAgent = agentPlanRepository
                .findByAgentInAndActiveTrue(agents)
                .stream()
                .collect(Collectors.groupingBy(plan -> plan.getAgent().getId()));

        return agents.stream()
                .map(agent -> AgentResponse.from(
                        agent,
                        pickDefaultPlan(plansByAgent.getOrDefault(agent.getId(), List.of()))
                ))
                .toList();

    }

    @Transactional(readOnly = true)
    public AgentResponse getAgent(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ایجنت پیدا نشد"));
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new RuntimeException("این مشاور در حال حاضر فعال نیست");
        }
        return AgentResponse.from(
                agent,
                pickDefaultPlan(
                        agentPlanRepository.findByAgentAndActiveTrueOrderByDurationDaysAsc(agent)
                )
        );
    }

    private AgentPlan pickDefaultPlan(List<AgentPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        return plans.stream()
                .filter(plan -> plan.getDurationDays() != null
                        && plan.getDurationDays() == defaultDurationDays)
                .findFirst()
                .orElse(plans.get(0));
    }

    public AgentResponse setActiveAgents(AgentRequest request) {
        /*List<Agent> byStatus = agentRepository.findByStatus(request.getStatus());
        if (!byStatus.isEmpty()) {
            throw  new RuntimeException("agent already active");
        }*/
        Agent agent = Agent.builder()
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
                .systemPrompt(request.getSystemPrompt())
                .avatar(request.getAvatar())
                .status(request.getStatus())
                .build();

        agentRepository.save(agent);
        return AgentResponse.from(agent);
    }
}
