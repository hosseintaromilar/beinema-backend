package com.coupleai.coupleai.beinema.Service.sms;

import com.coupleai.coupleai.beinema.Exception.SmsDeliveryException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
public class MelipayamakSmsProvider implements SmsProvider {

    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String senderNumber;

    public MelipayamakSmsProvider(
            ObjectMapper objectMapper,
            @Value("${melipayamak.api-url:https://console.melipayamak.com/api/send/simple}")
            String apiUrl,
            @Value("${melipayamak.api-key:}")
            String apiKey,
            @Value("${melipayamak.sender-number:}")
            String senderNumber
    ) {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.senderNumber = senderNumber;
    }

    @Override
    public void send(String toPhone, String text) {
        String key = trimToNull(apiKey);
        String sender = trimToNull(senderNumber);

        if (key == null || sender == null) {
            throw new SmsDeliveryException("تنظیمات پیامک کامل نیست.");
        }

        String endpoint = apiUrl.replaceAll("/$", "") + "/" + key;
        String payload = "{\"from\":\"" + escapeJson(sender)
                + "\",\"to\":\"" + escapeJson(toPhone)
                + "\",\"text\":\"" + escapeJson(text) + "\"}";

        HttpURLConnection connection = null;

        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);

            DataOutputStream output = new DataOutputStream(connection.getOutputStream());
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            output.write(body, 0, body.length);
            output.flush();
            output.close();

            int status = connection.getResponseCode();
            InputStream stream = status >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String responseBody = readStream(stream);

            if (!MelipayamakResponseParser.isSuccess(status, responseBody, objectMapper)) {
                throw new SmsDeliveryException("ارسال پیامک ناموفق بود.");
            }
        } catch (SmsDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SmsDeliveryException("ارسال پیامک ناموفق بود.", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
