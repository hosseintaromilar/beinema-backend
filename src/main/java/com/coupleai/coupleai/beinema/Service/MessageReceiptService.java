package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How far each person and the companion have received and read a room, by message
 * sequence number. The numbers only move forward. The page turns them into one tick
 * (saved), two ticks (everyone received it) and two blue ticks (everyone read it).
 */
@Service
@RequiredArgsConstructor
public class MessageReceiptService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final CurrentUserService currentUserService;
    private final ChatRoomEventHub eventHub;

    /** The page of the signed-in member says how far it has received and read. */
    @Transactional
    public void acknowledge(Long chatRoomId, Integer delivered, Integer read) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("گفتگو پیدا نشد."));
        User user = currentUserService.getCurrentUser();
        ChatRoomParticipant me = participantRepository.findByChatRoomAndUser(room, user)
                .orElseThrow(() -> new IllegalArgumentException("شما عضو این گفتگو نیستید."));
        // what was read was received too
        int newDelivered = max(me.getDeliveredSeq(), max(delivered, read));
        int newRead = max(me.getReadSeq(), read);
        if (newDelivered == max(me.getDeliveredSeq(), null) && newRead == max(me.getReadSeq(), null)) {
            return;
        }
        participantRepository.updateReceipts(me.getId(), newDelivered, newRead);
        publish(chatRoomId, user.getId(), newDelivered, newRead);
    }

    /** The companion picked these messages up (read=false) or has read them and decided (read=true). */
    @Transactional
    public void companionReached(Long chatRoomId, int sequence, boolean read) {
        chatRoomRepository.markCompanionDelivered(chatRoomId, sequence);
        if (read) {
            chatRoomRepository.markCompanionRead(chatRoomId, sequence);
        }
        publish(chatRoomId, null, sequence, read ? sequence : 0);
    }

    private void publish(Long chatRoomId, Long userId, int delivered, int read) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("deliveredSeq", delivered);
        payload.put("readSeq", read);
        eventHub.publishNamed(chatRoomId, "read_state", payload);
    }

    private static int max(Integer a, Integer b) {
        return Math.max(a == null ? 0 : a, b == null ? 0 : b);
    }
}
