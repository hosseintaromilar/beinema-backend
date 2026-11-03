package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.metis.CreateMetisConversationRequest;
import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.DTO.metis.MetisUserRequest;
import com.coupleai.coupleai.beinema.Entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class MetisServiceImpl implements MetisService {

    private final RestClient metisRestClient;

    private final MetisClient metisClient;

    private final com.coupleai.coupleai.beinema.ai.AiHealth aiHealth;

    /** Runs a call and tells the service's health how it went. */
    private <T> T watched(java.util.function.Supplier<T> call) {
        try {
            T result = call.get();
            aiHealth.succeeded();
            return result;
        } catch (IllegalArgumentException wrongInput) {
            throw wrongInput;
        } catch (RuntimeException failure) {
            aiHealth.failed(failure);
            throw failure;
        }
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MetisServiceImpl.class);

    /** Waits before each try of a removal: at once, then one and ten minutes later. */
    private static final long[] FORGET_DELAYS_SECONDS = {0, 60, 600};

    private static final java.util.concurrent.ScheduledExecutorService FORGETTING =
            java.util.concurrent.Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "metis-forget");
                thread.setDaemon(true);
                return thread;
            });

    @Override
    public void forget(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            forget(sessionId, 0);
        }
    }

    private void forget(String sessionId, int attempt) {
        FORGETTING.schedule(() -> {
            try {
                metisRestClient.delete()
                        .uri("/api/v1/chat/session/{id}", sessionId)
                        .retrieve()
                        .toBodilessEntity();
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound gone) {
                // already removed
            } catch (RuntimeException failure) {
                if (attempt + 1 < FORGET_DELAYS_SECONDS.length) {
                    forget(sessionId, attempt + 1);
                } else {
                    LOG.warn("Could not remove Metis conversation {}: {}", sessionId, failure.getMessage());
                }
            }
        }, FORGET_DELAYS_SECONDS[attempt], java.util.concurrent.TimeUnit.SECONDS);
    }


    @Override
    public MetisConversationResponse createConversation(
            String botId,
            User user
    ) {
        return createConversation(botId, String.valueOf(user.getId()), user.visibleName());
    }


    @Override
    public MetisConversationResponse createConversation(
            String botId,
            String userKey,
            String userName
    ) {
        return watched(() -> createConversationNow(botId, userKey, userName));
    }

    private MetisConversationResponse createConversationNow(
            String botId,
            String userKey,
            String userName
    ) {

        if (botId == null || botId.isBlank()) {

            throw new IllegalArgumentException(
                    "AI bot id is required"
            );
        }


        CreateMetisConversationRequest request =
                CreateMetisConversationRequest.builder()

                        .botId(botId)

                        .user(
                                MetisUserRequest.builder()

                                        .id(
                                                userKey
                                        )

                                        .name(
                                                userName
                                        )

                                        .build()
                        )

                        .build();


        try {

            MetisConversationResponse response =

                    metisRestClient

                            .post()

                            .uri(
                                    "/api/v1/chat/session"
                            )

                            .body(request)

                            .retrieve()

                            .onStatus(
                                    HttpStatusCode::isError,
                                    (request1, response1) -> {

                                        throw new com.coupleai.coupleai.beinema.ai.MetisCallException(
                                                com.coupleai.coupleai.beinema.ai.AiError.ofStatus(
                                                        response1.getStatusCode().value(), ""),
                                                "Metis conversation creation failed. HTTP status: "
                                                        + response1.getStatusCode()
                                        );

                                    }
                            )

                            .body(
                                    MetisConversationResponse.class
                            );


            if (
                    response == null
                            ||
                            response.getId() == null
                            ||
                            response.getId().isBlank()
            ) {

                throw new RuntimeException(
                        "Metis created conversation but returned no conversation id"
                );
            }


            return response;

        } catch (RestClientException exception) {

            throw new RuntimeException(
                    "Could not connect to Metis",
                    exception
            );
        }
    }


    @Override
    public void streamMessage(
            String sessionId,
            String content,
            Consumer<String> chunkConsumer
    ) {

        if (sessionId == null || sessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Metis session id is required"
            );
        }

        if (content == null || content.isBlank()) {

            throw new IllegalArgumentException(
                    "Message content is required"
            );
        }

        if (chunkConsumer == null) {

            throw new IllegalArgumentException(
                    "Chunk consumer is required"
            );
        }


        watched(() -> {
            metisClient.streamMessage(
                    sessionId,
                    content,
                    chunkConsumer
            );
            return null;
        });
    }


    @Override
    public String sendMessage(
            String sessionId,
            String content
    ) {

        String reply = sendMessageAllowBlank(sessionId, content);
        if (reply.isBlank()) {
            throw new RuntimeException("Metis returned an empty analysis reply");
        }
        return reply;
    }


    @Override
    public String sendMessageAllowBlank(
            String sessionId,
            String content
    ) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Metis session id is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }

        var response = watched(() -> metisClient.sendMessage(sessionId, content));
        String reply = response == null ? null : response.extractContent();
        return reply == null ? "" : reply;
    }

    @Override
    public String uploadFile(byte[] bytes, String filename, String contentType) {
        return watched(() -> metisClient.uploadFile(bytes, filename, contentType));
    }

    @Override
    public String sendMessageWithAttachments(
            String sessionId,
            String content,
            java.util.List<com.coupleai.coupleai.beinema.DTO.metis.MetisAttachment> attachments
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Metis session id is required");
        }
        var response = watched(() -> metisClient.sendMessage(sessionId, content, attachments));
        String reply = response == null ? null : response.extractContent();
        return reply == null ? "" : reply;
    }
}
