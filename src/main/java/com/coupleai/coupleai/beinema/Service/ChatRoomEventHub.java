package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatRoomEventHub {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomEventHub.class);

    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public ChatRoomEventHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(Long chatRoomId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.computeIfAbsent(chatRoomId, id -> new CopyOnWriteArrayList<>())
                .add(emitter);

        Runnable cleanup = () -> remove(chatRoomId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("ready")
                            .data("ok")
            );
        } catch (IOException | IllegalStateException ignored) {
            cleanup.run();
        }

        return emitter;
    }

    public void publish(Long chatRoomId, MessageResponse message) {
        List<SseEmitter> roomEmitters = emitters.get(chatRoomId);
        if (roomEmitters == null || roomEmitters.isEmpty() || message == null) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException exception) {
            log.warn("Could not serialize chat event for room {}", chatRoomId, exception);
            return;
        }

        for (SseEmitter emitter : roomEmitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("message")
                                .data(payload, MediaType.TEXT_PLAIN)
                );
            } catch (IOException | IllegalStateException exception) {
                remove(chatRoomId, emitter);
            }
        }
    }

    @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        emitters.forEach((chatRoomId, roomEmitters) -> {
            for (SseEmitter emitter : roomEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException exception) {
                    remove(chatRoomId, emitter);
                }
            }
        });
    }

    private void remove(Long chatRoomId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> roomEmitters = emitters.get(chatRoomId);
        if (roomEmitters == null) {
            return;
        }
        roomEmitters.remove(emitter);
        if (roomEmitters.isEmpty()) {
            emitters.remove(chatRoomId, roomEmitters);
        }
    }
}
