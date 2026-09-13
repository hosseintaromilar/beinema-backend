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
}
