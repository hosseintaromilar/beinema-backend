package com.coupleai.coupleai.beinema.DTO.Agent;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import lombok.Builder;

@Builder
public record AdminAgentResponse(

        Long id,

        String name,

        AgentType type,

        String description,

        String systemPrompt,

        String avatar,

        String headline,

        String bio,

        String specialties,

        String photoUrl,

        String aiBotId,

        AgentStatus status,

        Long price

) {

    public static AdminAgentResponse from(Agent agent, Long price) {
        return AdminAgentResponse.builder()
                .id(agent.getId())
                .name(agent.getName())
                .type(agent.getType())
                .description(agent.getDescription())
                .systemPrompt(agent.getSystemPrompt())
                .avatar(agent.getAvatar())
                .headline(agent.getHeadline())
                .bio(agent.getBio())
                .specialties(agent.getSpecialties())
                .photoUrl(agent.getPhotoUrl())
                .aiBotId(agent.getAiBotId())
                .status(agent.getStatus())
                .price(price)
                .build();
    }
}
