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


    @Override
    public MetisConversationResponse createConversation(
            String botId,
            User user
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
                                                String.valueOf(
                                                        user.getId()
                                                )
                                        )

                                        .name(
                                                user.getName()
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

                                        throw new RuntimeException(
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


        metisClient.streamMessage(
                sessionId,
                content,
                chunkConsumer
        );
    }


    @Override
    public String sendMessage(
            String sessionId,
            String content
    ) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Metis session id is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }

        var response = metisClient.sendMessage(sessionId, content);
        String reply = response == null ? null : response.extractContent();
        if (reply == null || reply.isBlank()) {
            throw new RuntimeException("Metis returned an empty analysis reply");
        }
        return reply;
    }
}