package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentSummary {

    private Long id;

    private String name;

    private String type;

    private String avatar;

}