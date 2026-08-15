package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.Entity.User;

public interface MetisService {

    MetisConversationResponse createConversation(
            String botId,
            User user
    );
}