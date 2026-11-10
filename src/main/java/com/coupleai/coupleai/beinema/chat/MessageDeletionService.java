package com.coupleai.coupleai.beinema.chat;

import com.coupleai.coupleai.beinema.files.ChatFileService;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * A person takes back one of their own messages. Its words and files are erased here; only a
 * mark that it was deleted stays, so the chat still reads in order. Metis keeps nothing to
 * erase: every counselor call is a fresh Metis conversation that is removed after its answer
 * (MetisService.forget), and the next call is built from this database without the message.
 */
@Service
@RequiredArgsConstructor
public class MessageDeletionService {

    private final MessageRepository messageRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ChatFileService chatFileService;
    private final ChatRoomEventHub eventHub;
    private final com.coupleai.coupleai.beinema.Repository.DeletionLogRepository deletionLogRepository;

    @Transactional
    public void deleteOwn(Long chatRoomId, Long messageId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("حساب پیدا نشد."));
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("این پیام پیدا نشد."));
        ChatRoom room = message.getChatRoom();
        if (room == null || !room.getId().equals(chatRoomId)
                || participantRepository.findByChatRoomAndUser(room, user).isEmpty()) {
            throw new IllegalArgumentException("این پیام پیدا نشد.");
        }
        if (message.getSenderType() != MessageSenderType.USER || !user.getId().equals(message.getSenderId())) {
            throw new IllegalArgumentException("فقط پیام‌های خودت را می‌توانی حذف کنی.");
        }
        if (Boolean.TRUE.equals(message.getDeleted())) {
            return;
        }
        message.setDeleted(true);
        message.setContent("");
        messageRepository.saveAndFlush(message);
        chatFileService.deleteMessageFiles(messageId);
        com.coupleai.coupleai.beinema.Entity.DeletionLog entry = new com.coupleai.coupleai.beinema.Entity.DeletionLog();
        entry.setScope(com.coupleai.coupleai.beinema.Entity.DeletionLog.MESSAGE);
        entry.setChatRoomId(chatRoomId);
        entry.setMessageId(messageId);
        entry.setActorId(user.getId());
        deletionLogRepository.save(entry);
        eventHub.publishNamed(chatRoomId, "message_deleted", Map.of("messageId", messageId));
    }
}
