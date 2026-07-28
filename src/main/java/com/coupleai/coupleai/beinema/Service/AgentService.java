package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Agent.AgentRequest;
import com.coupleai.coupleai.beinema.DTO.Agent.AgentResponse;
import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentService {


    private final AgentRepository agentRepository;


    public List<AgentResponse> getActiveAgents() {


        return agentRepository

                .findByStatus(AgentStatus.ACTIVE)

                .stream()

                .map(agent -> new AgentResponse(

                        agent.getId(),

                        agent.getName(),

                        agent.getType(),

                        agent.getDescription(),

                        agent.getAvatar()

                ))

                .toList();

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
