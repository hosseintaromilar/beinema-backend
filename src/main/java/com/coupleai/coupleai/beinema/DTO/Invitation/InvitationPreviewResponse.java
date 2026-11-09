package com.coupleai.coupleai.beinema.DTO.Invitation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvitationPreviewResponse {

    private String token;

    private String chatRoomTitle;

    private String inviterName;

    private String status;

    private Long chatRoomId;

    /** The companion's type (RELATIONSHIP, FINANCIAL...): its rules, and whether joining needs 18+. */
    private String agentType;
}
