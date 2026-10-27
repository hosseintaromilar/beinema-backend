package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChatRoomRequest {

        private Long agentId;
        private String title;

        /** The creator ticked «I accept the rules»; without it no room is made. */
        private Boolean rulesAccepted;

}