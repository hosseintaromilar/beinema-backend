package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.Entity.User;

import java.util.function.Consumer;

public interface MetisService {

    MetisConversationResponse createConversation(
            String botId,
            User user
    );

    /** A conversation for a platform caller that is not one person (for example a counseling room). */
    MetisConversationResponse createConversation(
            String botId,
            String userKey,
            String userName
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

    String sendMessageAllowBlank(
            String sessionId,
            String content
    );

    /** Uploads bytes to Metis storage and returns a direct-download URL. */
    String uploadFile(byte[] bytes, String filename, String contentType);

    /** Sends a message with attachments (image or file URLs) and returns the reply text. */
    String sendMessageWithAttachments(
            String sessionId,
            String content,
            java.util.List<com.coupleai.coupleai.beinema.DTO.metis.MetisAttachment> attachments
    );
}