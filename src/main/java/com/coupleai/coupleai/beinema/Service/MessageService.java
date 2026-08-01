package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;


import java.util.List;

public interface MessageService {

    MessageResponse sendMessage(
            Long chatRoomId,
            SendMessageRequest request
    );

    List<MessageResponse> getMessages(
            Long chatRoomId
    );


}
