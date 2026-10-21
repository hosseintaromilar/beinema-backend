package com.coupleai.coupleai.beinema.counseling;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notes that belong to a partner message but are not part of its text: the
 * platform notes ([SYSTEM | SESSION] PAUSE_EXPIRED ...) and the neutral reading
 * of an attached file. They wait here until the turn that decides about that
 * message has been stored, so a failed turn does not lose them.
 *
 * A note without a message is a moment of the session (it opened, a partner
 * entered it): it makes a turn of its own even when nobody wrote.
 */
@Component
public class TurnInbox {

    public record Note(Long messageId, String text) {
    }

    private final Map<Long, List<Note>> notes = new ConcurrentHashMap<>();

    public void add(Long roomId, Long messageId, String text) {
        if (roomId == null || text == null || text.isBlank()) {
            return;
        }
        notes.computeIfAbsent(roomId, key -> new ArrayList<>());
        synchronized (notes.get(roomId)) {
            notes.get(roomId).add(new Note(messageId, text.trim()));
        }
    }

    public List<Note> peek(Long roomId) {
        List<Note> list = notes.get(roomId);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    /**
     * Forgets what the finished turn has seen and the notes of messages up to and
     * including the processed one. A moment that arrived while the turn ran stays.
     */
    public void processed(Long roomId, Long upToMessageId, List<Note> seen) {
        List<Note> list = notes.get(roomId);
        if (list == null) {
            return;
        }
        synchronized (list) {
            if (seen != null) {
                list.removeAll(seen);
            }
            if (upToMessageId != null) {
                list.removeIf(note -> note.messageId() != null && note.messageId() <= upToMessageId);
            }
        }
    }
}
