package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.CounselingState;
import com.coupleai.coupleai.beinema.Repository.CounselingStateRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Reads and writes the structured memory of a room. A broken memory is never guessed at: it starts clean and is logged. */
@Service
@RequiredArgsConstructor
public class CounselingStateStore {

    private static final Logger log = LoggerFactory.getLogger(CounselingStateStore.class);

    static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final CounselingStateRepository repository;

    /** The stored state, or a new unsaved one for a room without memory yet. */
    public CounselingState load(Long roomId) {
        return repository.findByChatRoomId(roomId).orElseGet(() -> {
            CounselingState created = new CounselingState();
            created.setChatRoomId(roomId);
            return created;
        });
    }

    public static CounselingSnapshot snapshot(CounselingState state) {
        if (state == null || state.getStateJson() == null || state.getStateJson().isBlank()) {
            return new CounselingSnapshot();
        }
        try {
            return JSON.readValue(state.getStateJson(), CounselingSnapshot.class);
        } catch (Exception exception) {
            log.warn("Unreadable counseling memory of room {}: {}", state.getChatRoomId(), exception.getMessage());
            return new CounselingSnapshot();
        }
    }

    public static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not write JSON", exception);
        }
    }

    /** Saves within the caller's transaction; the version check refuses a concurrent overwrite. */
    public CounselingState save(CounselingState state, CounselingSnapshot snapshot) {
        state.setStateJson(json(snapshot));
        return repository.save(state);
    }
}
