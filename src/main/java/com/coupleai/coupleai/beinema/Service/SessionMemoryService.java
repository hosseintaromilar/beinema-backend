package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.SessionMemory;
import com.coupleai.coupleai.beinema.Repository.SessionMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionMemoryService {

    private final SessionMemoryRepository sessionMemoryRepository;

    @Transactional
    public void store(Long chatRoomId, String producedState) {
        if (chatRoomId == null || producedState == null || producedState.isBlank()) {
            return;
        }
        SessionMemory memory = sessionMemoryRepository.findByChatRoomId(chatRoomId)
                .orElseGet(() -> {
                    SessionMemory created = new SessionMemory();
                    created.setChatRoomId(chatRoomId);
                    return created;
                });
        memory.setSnapshotJson(producedState);
        sessionMemoryRepository.save(memory);
    }
}
