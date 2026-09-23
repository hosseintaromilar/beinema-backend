package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import com.coupleai.coupleai.beinema.governance.GovernanceAction;
import com.coupleai.coupleai.beinema.governance.GovernanceTarget;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GovernanceEventPayload {

    private String type;
    private Long chatRoomId;
    private GovernanceAction action;
    private GovernanceTarget target;
    private List<Long> userIds;
    private Long pausedUntil;
    private Long remainingSeconds;
    private String message;
    private Boolean reentryPending;
    private Boolean sessionConsented;
}
