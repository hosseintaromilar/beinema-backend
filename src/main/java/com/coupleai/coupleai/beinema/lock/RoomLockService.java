package com.coupleai.coupleai.beinema.lock;

import com.coupleai.coupleai.beinema.Entity.RoomLock;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.RoomLockRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A conversation's lock, for one person. With a lock, every request inside the room needs a key that
 * only the PIN gives; the key lives in this server's memory, belongs to that person and room, and ends
 * after a while without use (or when the server restarts: they type the PIN again).
 */
@Service
public class RoomLockService {

    static final Duration KEY_IDLE = Duration.ofMinutes(20);
    static final int MAX_TRIES = 5;
    static final Duration BLOCK = Duration.ofMinutes(15);
    static final Duration RESET_WINDOW = Duration.ofHours(24);

    private final RoomLockRepository repository;
    private final ChatRoomRepository rooms;
    private final ChatRoomParticipantRepository participants;
    private final BCryptPasswordEncoder hasher = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Key> keys = new ConcurrentHashMap<>();

    record Key(Long userId, Long roomId, Instant lastUse) {
    }

    public record Status(boolean locked, boolean open, boolean resetAllowed, long blockedSeconds) {
    }

    public RoomLockService(RoomLockRepository repository, ChatRoomRepository rooms, ChatRoomParticipantRepository participants) {
        this.repository = repository;
        this.rooms = rooms;
        this.participants = participants;
    }

    /** 4 to 8 digits; Persian and Arabic digits count as digits. */
    static String pin(String raw) {
        StringBuilder out = new StringBuilder();
        for (char c : (raw == null ? "" : raw.trim()).toCharArray()) {
            if (c >= '۰' && c <= '۹') {
                out.append((char) ('0' + (c - '۰')));
            } else if (c >= '٠' && c <= '٩') {
                out.append((char) ('0' + (c - '٠')));
            } else {
                out.append(c);
            }
        }
        String pin = out.toString();
        if (!pin.matches("\\d{4,8}")) {
            throw new IllegalArgumentException("رمز گفتگو باید ۴ تا ۸ رقم باشد.");
        }
        return pin;
    }

    public boolean isLocked(Long userId, Long roomId) {
        return repository.existsByUserIdAndChatRoomId(userId, roomId);
    }

    public Set<Long> lockedRooms(Long userId, Collection<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return Set.of();
        }
        return repository.findAllByUserIdAndChatRoomIdIn(userId, roomIds).stream()
                .map(RoomLock::getChatRoomId).collect(Collectors.toSet());
    }

    /** Whether this key opens this room for this person now; a key in use stays alive. */
    public boolean opens(String key, Long userId, Long roomId) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Key found = keys.get(key);
        if (found == null || !found.userId().equals(userId) || !found.roomId().equals(roomId)) {
            return false;
        }
        if (found.lastUse().plus(KEY_IDLE).isBefore(Instant.now())) {
            keys.remove(key);
            return false;
        }
        keys.put(key, new Key(userId, roomId, Instant.now()));
        return true;
    }

    public Status status(Long userId, Long roomId, String key, Instant signedInAt) {
        requireMember(userId, roomId);
        RoomLock lock = repository.findByUserIdAndChatRoomId(userId, roomId).orElse(null);
        if (lock == null) {
            return new Status(false, true, false, 0);
        }
        return new Status(true, opens(key, userId, roomId), resetAllowed(lock, signedInAt), blockedSeconds(lock));
    }

    /** Puts a lock on, or changes it; changing needs the current PIN (or an open key). */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public String set(Long userId, Long roomId, String newPin, String currentPin, String key) {
        requireMember(userId, roomId);
        String clean = pin(newPin);
        RoomLock lock = repository.findByUserIdAndChatRoomId(userId, roomId).orElse(null);
        if (lock != null && !opens(key, userId, roomId)) {
            check(lock, currentPin);
        }
        if (lock == null) {
            lock = new RoomLock();
            lock.setUserId(userId);
            lock.setChatRoomId(roomId);
        }
        lock.setPinHash(hasher.encode(clean));
        lock.setFailedCount(0);
        lock.setBlockedUntil(null);
        lock.setResetRequestedAt(null);
        repository.save(lock);
        forget(userId, roomId);
        return issue(userId, roomId);
    }

    /** Takes the lock off; needs the PIN or an open key. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void remove(Long userId, Long roomId, String currentPin, String key) {
        RoomLock lock = repository.findByUserIdAndChatRoomId(userId, roomId).orElse(null);
        if (lock == null) {
            return;
        }
        if (!opens(key, userId, roomId)) {
            check(lock, currentPin);
        }
        repository.delete(lock);
        forget(userId, roomId);
    }

    /** The PIN typed at the door: a key when it is right. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public String unlock(Long userId, Long roomId, String typed) {
        requireMember(userId, roomId);
        RoomLock lock = repository.findByUserIdAndChatRoomId(userId, roomId).orElse(null);
        if (lock == null) {
            return issue(userId, roomId);
        }
        check(lock, typed);
        return issue(userId, roomId);
    }

    /** They forgot the PIN: after they sign out and back in, the lock may be removed. */
    @Transactional
    public void requestReset(Long userId, Long roomId) {
        repository.findByUserIdAndChatRoomId(userId, roomId).ifPresent(lock -> {
            lock.setResetRequestedAt(Instant.now());
            repository.save(lock);
        });
        forget(userId, roomId);
    }

    /** After a fresh sign-in: the forgotten lock is removed and the room opens. */
    @Transactional
    public String reset(Long userId, Long roomId, Instant signedInAt) {
        RoomLock lock = repository.findByUserIdAndChatRoomId(userId, roomId)
                .orElseThrow(() -> new IllegalArgumentException("این گفتگو قفل نیست."));
        if (!resetAllowed(lock, signedInAt)) {
            throw new IllegalArgumentException("برای برداشتن قفل، اول «رمز را فراموش کردم» را بزن و دوباره وارد حسابت شو.");
        }
        repository.delete(lock);
        forget(userId, roomId);
        return issue(userId, roomId);
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        repository.deleteByChatRoomId(roomId);
        keys.values().removeIf(key -> key.roomId().equals(roomId));
    }

    static boolean resetAllowed(RoomLock lock, Instant signedInAt) {
        Instant asked = lock.getResetRequestedAt();
        return asked != null && signedInAt != null && signedInAt.isAfter(asked)
                && Instant.now().isBefore(asked.plus(RESET_WINDOW));
    }

    private void check(RoomLock lock, String typed) {
        long blocked = blockedSeconds(lock);
        if (blocked > 0) {
            throw new IllegalArgumentException("چند بار رمز اشتباه زده شد. " + Math.max(1, (blocked + 59) / 60)
                    + " دقیقهٔ دیگر دوباره امتحان کن.");
        }
        String clean;
        try {
            clean = pin(typed);
        } catch (IllegalArgumentException wrongShape) {
            clean = null;
        }
        if (clean != null && hasher.matches(clean, lock.getPinHash())) {
            lock.setFailedCount(0);
            lock.setBlockedUntil(null);
            repository.save(lock);
            return;
        }
        int failed = lock.getFailedCount() + 1;
        if (failed >= MAX_TRIES) {
            lock.setFailedCount(0);
            lock.setBlockedUntil(Instant.now().plus(BLOCK));
            repository.save(lock);
            throw new IllegalArgumentException("رمز اشتباه بود. تا ۱۵ دقیقه نمی‌شود دوباره امتحان کرد.");
        }
        lock.setFailedCount(failed);
        repository.save(lock);
        throw new IllegalArgumentException("رمز اشتباه است. " + (MAX_TRIES - failed) + " بار دیگر می‌شود امتحان کرد.");
    }

    private static long blockedSeconds(RoomLock lock) {
        Instant until = lock.getBlockedUntil();
        return until == null ? 0 : Math.max(0, Duration.between(Instant.now(), until).getSeconds());
    }

    private String issue(Long userId, Long roomId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        keys.put(key, new Key(userId, roomId, Instant.now()));
        return key;
    }

    private void forget(Long userId, Long roomId) {
        keys.values().removeIf(key -> key.userId().equals(userId) && key.roomId().equals(roomId));
    }

    private void requireMember(Long userId, Long roomId) {
        if (!rooms.existsById(roomId)) {
            throw new IllegalArgumentException("گفتگو پیدا نشد.");
        }
        boolean member = participants.existsByChatRoom_IdAndUser_Id(roomId, userId);
        if (!member) {
            throw new IllegalArgumentException("شما عضو این گفتگو نیستید.");
        }
    }
}
