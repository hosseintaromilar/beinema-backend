package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.Entity.User;

import java.util.function.Consumer;

public interface MetisService {

    MetisConversationResponse createConversation(
            String botId,
            User user
    );


    void streamMessage(
            String sessionId,
            String content,
            Consumer<String> chunkConsumer
    );

    String sendMessage(
            String sessionId,
            String content
    );
}