package com.coupleai.coupleai.beinema.DTO.Agent;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentPlanResponse {

    private Long id;

    private Long agentId;

    private String title;

    private Integer durationDays;

    private Long price;

    private boolean active;
}
