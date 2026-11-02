package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Exception.QuestionAnswerException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A sealed message to the companion. Only its sender sees it and the reply; the other person
 * sees no trace. It never reaches the director, the memory, the summaries or the report, so
 * it cannot turn the companion toward one side. Its own lane answers: safety first, otherwise
 * help with saying the thing in the shared room. All that leaves it is a nameless caution:
 * no face-to-face work in this room.
 */
@Service
@RequiredArgsConstructor
public class ConfidentialMessageService {

    private static final Logger log = LoggerFactory.getLogger(ConfidentialMessageService.class);

    public static final String MODE = "##BEINEMA_MODE=PRIVATE##";
    static final int MAX_TEXT = 2000;
    static final int MAX_PER_DAY = 5;
    private static final int SHARED_WINDOW = 6;
    private static final int OWN_WINDOW = 4;

    static final String SAFETY_FALLBACK = "حرفت رو جدی می‌گیرم. الان در امانی؟ اگه خطری هست، اورژانس اجتماعی ۱۲۳ و صدای مشاور ۱۴۸۰ کنارت هستن و در خطر فوری با ۱۱۰ یا ۱۱۵ تماس بگیر.";
    static final String PLAIN_FALLBACK = "پیامت رسید، ولی الان نتونستم جواب بدم. کمی بعد دوباره امتحان کن.";

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final ChatRealtimeService chatRealtimeService;
    private final BrainGateway brain;

    public Message send(Long roomId, String content, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new QuestionAnswerException("NOT_MEMBER", HttpStatus.NOT_FOUND, "این گفتگو پیدا نشد."));
        ChatRoomParticipant me = participantRepository.findByChatRoomAndUser(room, user)
                .orElseThrow(() -> new QuestionAnswerException("NOT_MEMBER", HttpStatus.NOT_FOUND, "این گفتگو پیدا نشد."));
        if (room.getStatus() != ChatRoomStatus.ACTIVE || !me.isActiveMember()) {
            throw new QuestionAnswerException("ROOM_ENDED", HttpStatus.CONFLICT, "این گفتگو دیگر فعال نیست.");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw new QuestionAnswerException("TEXT_INVALID", HttpStatus.BAD_REQUEST, "متن پیام خصوصی خالی یا خیلی طولانی است.");
        }
        boolean danger = screen(text).any();
        long today = messageRepository.countConfidentialSince(roomId, user.getId(), MessageSenderType.USER,
                LocalDateTime.now().minusDays(1));
        // a person in danger is never told to wait
        if (!danger && today >= MAX_PER_DAY) {
            throw new QuestionAnswerException("TOO_MANY", HttpStatus.TOO_MANY_REQUESTS,
                    "سقف پیام خصوصی امروز پر شده. حرفت رو می‌تونی توی گفتگو بگی. اگه موضوع امنیتته، همین رو بنویس.");
        }
        // the shared numbering does not move: a gap would show the other person that something is hidden
        int next = messageRepository.findMaxSequenceNumberByChatRoom(room).orElse(0);
        Message message = messageRepository.saveAndFlush(Message.builder()
                .chatRoom(room)
                .senderType(MessageSenderType.USER)
                .senderId(user.getId())
                .content(text)
                .status(MessageStatus.SENT)
                .sequenceNumber(next)
                .visibleToUserId(user.getId())
                .confidential(true)
                .build());
        Thread thread = new Thread(() -> answer(room, user, message, danger), "beinema-private-" + roomId);
        thread.setDaemon(true);
        thread.start();
        return message;
    }

    private void answer(ChatRoom room, User user, Message message, boolean danger) {
        Reply reply = null;
        try {
            reply = read(brain.forRoom(room).confidential(packet(room, user, message)).text());
        } catch (RuntimeException exception) {
            log.warn("Private reply failed in room {}: {}", room.getId(), exception.getMessage());
        }
        boolean safety = danger || (reply != null && reply.safety());
        if (safety) {
            chatRoomRepository.markSafetyCaution(room.getId());
        }
        String text = reply != null ? reply.text() : danger ? SAFETY_FALLBACK : PLAIN_FALLBACK;
        Long agentId = chatRoomAgentRepository.findActiveAgentIds(room.getId()).stream().findFirst().orElse(0L);
        chatRealtimeService.saveConfidentialReplyAndPublish(room.getId(), agentId, user.getId(), text);
    }

    private String packet(ChatRoom room, User user, Message message) {
        List<Message> all = messageRepository.findByChatRoomIdAndDeletedFalseOrderBySequenceNumberAsc(room.getId());
        StringBuilder out = new StringBuilder(MODE).append("\n[PERSON]\n").append(user.visibleName()).append('\n');
        out.append("[SHARED_ROOM] the last messages everyone can see\n");
        List<Message> shared = all.stream().filter(MessageTexts::countsAsConversation).toList();
        shared.subList(Math.max(0, shared.size() - SHARED_WINDOW), shared.size())
                .forEach(item -> out.append(item.getSenderType()).append(": ").append(PersianText.clip(MessageTexts.visible(item), 300)).append('\n'));
        out.append("[EARLIER_PRIVATE] with this person only\n");
        List<Message> own = all.stream()
                .filter(item -> Boolean.TRUE.equals(item.getConfidential()) && user.getId().equals(item.getVisibleToUserId())
                        && !item.getId().equals(message.getId()))
                .toList();
        own.subList(Math.max(0, own.size() - OWN_WINDOW), own.size())
                .forEach(item -> out.append(item.getSenderType()).append(": ").append(PersianText.clip(item.getContent(), 300)).append('\n'));
        out.append("[MESSAGE]\n").append(message.getContent());
        return MODE + "\n" + DirectorPacketBuilder.neutralize(out.substring(MODE.length() + 1));
    }

    record Reply(String text, boolean safety) {
    }

    static Reply read(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode root = CounselingStateStore.JSON.readTree(raw.substring(start, end + 1));
            String text = root.path("reply").asText("").trim();
            return text.isEmpty() ? null : new Reply(text, "SAFETY".equals(root.path("kind").asText("")));
        } catch (Exception exception) {
            return null;
        }
    }

    private static SafetyScreen.Result screen(String text) {
        return SafetyScreen.screen(List.of(new SafetyScreen.Input(null, "OWNER", text)));
    }
}
