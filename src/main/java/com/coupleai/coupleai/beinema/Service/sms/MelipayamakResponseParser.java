package com.coupleai.coupleai.beinema.Service.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class MelipayamakResponseParser {

    private MelipayamakResponseParser() {
    }

    static boolean isSuccess(int httpStatus, String body, ObjectMapper objectMapper) {
        if (httpStatus < 200 || httpStatus >= 300) {
            return false;
        }

        if (body == null || body.isBlank()) {
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode recId = root.get("recId");
            if (recId == null || recId.isNull()) {
                recId = root.get("RecId");
            }
            if (recId == null || recId.isNull()) {
                return false;
            }
            if (recId.isNumber()) {
                return recId.asLong() > 0;
            }
            return Long.parseLong(recId.asText().trim()) > 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
