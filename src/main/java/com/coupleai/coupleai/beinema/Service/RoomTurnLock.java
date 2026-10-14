package com.coupleai.coupleai.beinema.Service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes calls to one Metis session so two partners writing at the same
 * moment cannot overwrite each other's case notes. Messages are stored at once;
 * only their processing waits in line. Invisible to users.
 */
@Component
public class RoomTurnLock {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void lock(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return;
        }
        locks.computeIfAbsent(sessionKey, key -> new ReentrantLock(true)).lock();
    }

    public void unlock(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return;
        }
        ReentrantLock lock = locks.get(sessionKey);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public <T> T locked(String sessionKey, Supplier<T> action) {
        lock(sessionKey);
        try {
            return action.get();
        } finally {
            unlock(sessionKey);
        }
    }
}
