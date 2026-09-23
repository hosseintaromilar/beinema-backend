package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParticipantSummary {

    private Long userId;

    private String name;

    private ParticipantRole role;

    private Boolean paused;

    private Long pausedUntil;

    private Boolean reentryPending;

    private Boolean sessionConsented;
}
