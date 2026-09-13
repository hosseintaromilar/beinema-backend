package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.metis.MetisMessage;
import com.coupleai.coupleai.beinema.DTO.metis.MetisMessageRequest;
import com.coupleai.coupleai.beinema.DTO.metis.MetisMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class MetisClient {

    @Value("${metis.base-url}")
    private String baseUrl;

    @Value("${metis.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient =
            HttpClient.newHttpClient();


    /**
     * Send a normal non-streaming message to Metis.
     */
    public MetisMessageResponse sendMessage(
            String sessionId,
            String content
    ) {

        try {

            MetisMessage message =
                    MetisMessage.builder()
                            .content(content)
                            .type("USER")
                            .build();


            MetisMessageRequest requestBody =
                    MetisMessageRequest.builder()
                            .message(message)
                            .build();


            String json =
                    objectMapper.writeValueAsString(
                            requestBody
                    );


            String url =
                    baseUrl
                            + "/api/v1/chat/session/"
                            + sessionId
                            + "/message";


            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(url)
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + apiKey
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();


            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );


            if (
                    response.statusCode() < 200
                            ||
                            response.statusCode() >= 300
            ) {

                throw new RuntimeException(
                        "Metis returned HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }


            return objectMapper.readValue(
                    response.body(),
                    MetisMessageResponse.class
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Could not send message to Metis",
                    e
            );
        }
    }


    /**
     * Send a message to Metis and receive the AI response
     * as a stream of tokens/chunks.
     *
     * Every received AI content chunk is passed to
     * chunkConsumer exactly as received from Metis.
     */
    public void streamMessage(
            String sessionId,
            String content,
            Consumer<String> chunkConsumer
    ) {

        if (
                sessionId == null
                        ||
                        sessionId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Metis session id is required"
            );
        }


        if (
                content == null
                        ||
                        content.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Message content is required"
            );
        }


        if (chunkConsumer == null) {

            throw new IllegalArgumentException(
                    "Chunk consumer is required"
            );
        }


        try {

            /*
             * Build request body.
             */

            MetisMessage message =
                    MetisMessage.builder()
                            .content(content)
                            .type("USER")
                            .build();


            MetisMessageRequest requestBody =
                    MetisMessageRequest.builder()
                            .message(message)
                            .build();


            String json =
                    objectMapper.writeValueAsString(
                            requestBody
                    );


            /*
             * Streaming endpoint.
             */

            String url =
                    baseUrl
                            + "/api/v1/chat/session/"
                            + sessionId
                            + "/message/stream";


            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(url)
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + apiKey
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "Accept",
                                    "text/event-stream"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();


            /*
             * Receive response as InputStream.
             */

            HttpResponse<InputStream> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofInputStream()
                    );


            /*
             * Check HTTP status.
             */

            if (
                    response.statusCode() < 200
                            ||
                            response.statusCode() >= 300
            ) {

                String errorBody =
                        readStream(
                                response.body()
                        );

                throw new RuntimeException(
                        "Metis streaming request failed. HTTP "
                                + response.statusCode()
                                + ": "
                                + errorBody
                );
            }


            /*
             * Read streaming response.
             */

            try (
                    InputStream inputStream =
                            response.body();

                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            inputStream,
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                String line;


                while (
                        (line = reader.readLine())
                                != null
                ) {

                    /*
                     * Do NOT trim the line.
                     *
                     * We only use isBlank() to detect
                     * an empty line.
                     */
                    if (line.isBlank()) {
                        continue;
                    }


                    /*
                     * Remove only the SSE protocol prefix.
                     *
                     * We intentionally do NOT trim the payload.
                     */

                    if (
                            line.startsWith("data:")
                    ) {

                        line =
                                line.substring(5);


                        /*
                         * Remove only the single optional
                         * protocol separator after "data:".
                         */
                        if (
                                line.startsWith(" ")
                        ) {

                            line =
                                    line.substring(1);
                        }
                    }


                    /*
                     * Ignore SSE termination marker.
                     */

                    if (
                            "[DONE]".equals(line)
                    ) {

                        break;
                    }


                    /*
                     * Parse one Metis stream object.
                     */

                    MetisMessageResponse streamResponse;


                    try {

                        streamResponse =
                                objectMapper.readValue(
                                        line,
                                        MetisMessageResponse.class
                                );

                    } catch (Exception parseException) {

                        /*
                         * Log malformed/unrecognized events
                         * without destroying the stream.
                         */

                        System.err.println(
                                "Could not parse Metis stream chunk: "
                                        + line
                        );

                        continue;
                    }


                    String chunk =
                            streamResponse.extractContent();

                    if (chunk != null) {
                        chunkConsumer.accept(chunk);
                    }

                    if (streamResponse.isFinished()) {
                        break;
                    }
                }
            }


        } catch (Exception e) {

            throw new RuntimeException(
                    "Could not stream message from Metis",
                    e
            );
        }
    }


    /**
     * Read an InputStream completely.
     *
     * Used only for HTTP error responses.
     */
    private String readStream(
            InputStream inputStream
    ) {

        try {

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            return "Unable to read Metis error response";
        }
    }
}
