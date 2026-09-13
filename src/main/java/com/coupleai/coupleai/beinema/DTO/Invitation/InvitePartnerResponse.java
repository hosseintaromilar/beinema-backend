package com.coupleai.coupleai.beinema.DTO.Invitation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvitePartnerResponse {

    private String message;

    private Long chatRoomId;
}
