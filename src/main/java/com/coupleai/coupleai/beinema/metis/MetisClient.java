package com.coupleai.coupleai.beinema.metis;

import com.coupleai.coupleai.beinema.DTO.metis.MetisMessage;
import com.coupleai.coupleai.beinema.DTO.metis.MetisMessageRequest;
import com.coupleai.coupleai.beinema.DTO.metis.MetisMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

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
    public MetisMessageResponse sendMessage(String sessionId, String content) {
        return sendMessage(sessionId, content, null);
    }

    public MetisMessageResponse sendMessage(
            String sessionId,
            String content,
            java.util.List<com.coupleai.coupleai.beinema.DTO.metis.MetisAttachment> attachments
    ) {
        return doSendMessage(sessionId, content, attachments);
    }

    private MetisMessageResponse doSendMessage(
            String sessionId,
            String content,
            java.util.List<com.coupleai.coupleai.beinema.DTO.metis.MetisAttachment> attachments
    ) {

        try {

            MetisMessage message =
                    MetisMessage.builder()
                            .content(content)
                            .type("USER")
                            .attachments(attachments)
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
                            .timeout(java.time.Duration.ofSeconds(60))
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

                com.coupleai.coupleai.beinema.ai.MetisCallException.check(response.statusCode(), response.body(), "Metis message");
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
     * Uploads a file to Metis storage and returns the public direct-download URL
     * that can be used as an attachment of a message.
     */
    public String uploadFile(byte[] bytes, String filename, String contentType) {
        try {
            String boundary = "----beinema" + java.util.UUID.randomUUID();
            String safeName = (filename == null || filename.isBlank() ? "file" : filename)
                    .replace("\"", "").replace("\r", "").replace("\n", "");
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"files\"; filename=\"" + safeName + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n";
            body.write(head.getBytes(StandardCharsets.UTF_8));
            body.write(bytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/storage"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                com.coupleai.coupleai.beinema.ai.MetisCallException.check(response.statusCode(), response.body(), "Metis storage");
            }
            var files = objectMapper.readTree(response.body()).path("files");
            String url = files.isArray() && files.size() > 0 ? files.get(0).path("url").asText("") : "";
            if (url.isBlank()) {
                throw new RuntimeException("Metis storage returned no url");
            }
            return url;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Could not upload file to Metis", exception);
        }
    }




}
