package com.coupleai.coupleai.beinema.DTO.Invitation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AcceptInvitationResponse {

    private Long chatRoomId;

    private String chatRoomTitle;

    private String message;
}
