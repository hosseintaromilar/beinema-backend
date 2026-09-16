package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import com.coupleai.coupleai.beinema.Enum.AgentAccessStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentAccessResponse {

    private Long id;

    private Long chatRoomId;

    private Long agentId;

    private Long planId;

    private String planTitle;

    private Long price;

    private Long payerId;

    private AgentAccessStatus status;

    private Long startsAt;

    private Long expiresAt;

    private boolean active;
}
