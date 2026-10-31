package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChatRoomRequest {

        private Long agentId;
        private String title;

        /** Optional: in the creator's words, why they open this conversation. */
        private String note;

        /** The creator ticked «I accept the rules»; without it no room is made. */
        private Boolean rulesAccepted;

}