package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatRoomEventHub {

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

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
        if (roomEmitters == null || roomEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : roomEmitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("message")
                                .data(message, MediaType.APPLICATION_JSON)
                );
            } catch (IOException | IllegalStateException exception) {
                remove(chatRoomId, emitter);
            }
        }
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
