package com.coupleai.coupleai.beinema.DTO.metis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetisMessageResponse {

    /**
     * Official Metis stream chunks look like:
     * { "id": "...", "content": "token", "done": false }
     */
    private String id;

    private String content;

    private Boolean done;

    /**
     * Older nested shape still accepted:
     * { "message": { "content": "token" }, "finishReason": "stop" }
     */
    private MetisStreamMessage message;

    private String finishReason;

    public String extractContent() {

        if (message != null && message.getContent() != null) {
            return message.getContent();
        }

        return content;
    }

    public boolean isFinished() {

        return Boolean.TRUE.equals(done);
    }
}

