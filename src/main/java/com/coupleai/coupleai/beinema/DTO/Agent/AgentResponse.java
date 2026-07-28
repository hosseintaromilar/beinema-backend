package com.coupleai.coupleai.beinema.DTO.Agent;

import com.coupleai.coupleai.beinema.DTO.User.UserResponse;
import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import lombok.Builder;

@Builder
public record AgentResponse(

        Long id,

        String name,

        AgentType type,

        String description,

        String avatar

) {

    public static AgentResponse from(Agent agent) {

        return AgentResponse.builder()

                .id(agent.getId())
                .name(agent.getName())
                .type(agent.getType())
                .description(agent.getDescription())
                .avatar(agent.getAvatar())
                .build();

    }

}