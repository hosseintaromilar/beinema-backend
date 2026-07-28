package com.coupleai.coupleai.beinema.DTO.Agent;

import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRequest {

    private String name;

    private AgentType type;

    private String description;

    private String systemPrompt;

    private String avatar;

    private AgentStatus status;
}