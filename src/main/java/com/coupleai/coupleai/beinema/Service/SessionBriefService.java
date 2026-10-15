package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.governance.CounselorSignal;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the counselor's Metis session short.
 *
 * The workflow reads the whole session history on every turn, so the cost of
 * one message grows with the length of the conversation. After a number of
 * messages the room is moved to a fresh Metis session. That session starts
 * with a private brief (what was established so far, plus the last few
 * messages) attached to the next message, so the counselor continues the same
 * thread. The messages themselves stay in our database; the couple sees one
 * unbroken conversation.
 *
 * This class only prepares and delivers the brief. Reading it and deciding
 * what to do next stays in the workflow.
 */
@Service
@RequiredArgsConstructor
public class SessionBriefService {

    private static final Logger log = LoggerFactory.getLogger(SessionBriefService.class);

    /** After a failed attempt wait a little before trying the same room again. */
    private static final long RETRY_AFTER_MILLIS = 5 * 60 * 1000L;

    private static final int BRIEF_MAX_CHARS = 1800;
    private static final int RECENT_MESSAGE_CHARS = 280;
    private static final int SUMMARY_INPUT_CHARS = 14000;

    @Value("${beinema.session.rotate-after-messages:24}")
    private int rotateAfterMessages;

    @Value("${beinema.session.keep-recent:6}")
    private int keepRecent;

    @Value("${metis.insight-bot-id}")
    private String insightBotId;

    private final MessageRepository messageRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MetisService metisService;

    private final Map<Long, Long> lastFailureAt = new ConcurrentHashMap<>();

    public boolean shouldRotate(ChatRoom chatRoom) {
        if (rotateAfterMessages <= 0 || chatRoom == null || chatRoom.getId() == null) {
            return false;
        }
        Long failedAt = lastFailureAt.get(chatRoom.getId());
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MILLIS) {
            return false;
        }
        return messagesInSession(chatRoom) >= rotateAfterMessages;
    }

    public int currentSequence(ChatRoom chatRoom) {
        return messageRepository.findMaxSequenceNumberByChatRoom(chatRoom).orElse(0);
    }

    public void noteFailure(ChatRoom chatRoom) {
        if (chatRoom != null && chatRoom.getId() != null) {
            lastFailureAt.put(chatRoom.getId(), System.currentTimeMillis());
        }
    }

    private int messagesInSession(ChatRoom chatRoom) {
        int start = chatRoom.getAiSessionStartSequence() == null ? 0 : chatRoom.getAiSessionStartSequence();
        return Math.max(0, currentSequence(chatRoom) - start);
    }

    /**
     * Builds the brief for the next session: an updated summary and the most
     * recent messages. Throws when the summary cannot be produced.
     */
    public Brief buildBrief(ChatRoom chatRoom, User user) {
        List<Message> all = messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom);
        Map<Long, String> labels = participantLabels(chatRoom);

        int start = chatRoom.getAiSessionStartSequence() == null ? 0 : chatRoom.getAiSessionStartSequence();
        List<String> sinceLast = new ArrayList<>();
        for (Message message : all) {
            if (message.getSequenceNumber() != null && message.getSequenceNumber() <= start) {
                continue;
            }
            String line = line(message, labels, 600);
            if (line != null) {
                sinceLast.add(line);
            }
        }
        if (sinceLast.isEmpty()) {
            throw new IllegalStateException("nothing new to summarise");
        }

        String transcript = String.join("\n", sinceLast);
        if (transcript.length() > SUMMARY_INPUT_CHARS) {
            transcript = transcript.substring(transcript.length() - SUMMARY_INPUT_CHARS);
        }

        String previous = chatRoom.getAiSessionBrief() == null ? "" : chatRoom.getAiSessionBrief();
        String summary = summarise(previous, transcript, user);

        // the very last message is the one about to be sent; it travels on its own
        List<Message> visible = new ArrayList<>();
        for (Message message : all) {
            if (line(message, labels, 1) != null) {
                visible.add(message);
            }
        }
        if (!visible.isEmpty()
                && visible.get(visible.size() - 1).getSenderType() == MessageSenderType.USER) {
            visible.remove(visible.size() - 1);
        }
        int from = Math.max(0, visible.size() - keepRecent);
        List<String> recent = new ArrayList<>();
        for (Message message : visible.subList(from, visible.size())) {
            recent.add(line(message, labels, RECENT_MESSAGE_CHARS));
        }

        String block = "[SYSTEM | SESSION]\n"
                + "EVENT=SESSION_CONTINUED\n"
                + "This is the same counseling session, moved to a fresh technical session. "
                + "The block below is private background for the counselor. Never mention it to the couple.\n"
                + "BRIEF_SO_FAR:\n" + summary + "\n"
                + "RECENT_MESSAGES:\n" + String.join("\n", recent) + "\n"
                + "END_OF_BRIEF\n";

        return new Brief(summary, block);
    }

    private String summarise(String previous, String transcript, User user) {
        String prompt = """
                تو دستیار یادداشت‌بردار یک جلسه‌ی مشاوره‌ی زوج هستی. فقط خلاصه‌ی خصوصی برای مشاور بنویس؛ با زوج حرف نزن و نصیحت نکن.
                از «خلاصه‌ی قبلی» و «گفتگوی تازه» یک خلاصه‌ی به‌روز و فشرده بنویس. فقط چیزهایی که واقعاً گفته شده؛ حدس را حقیقت نکن.
                فقط متن ساده، با همین عنوان‌ها، هر عنوان حداکثر دو جمله، در مجموع کمتر از ۱۵۰۰ نویسه:
                - چه چیزی هر نفر را به جلسه آورده (به نام هر نفر)
                - هر نفر چه می‌خواهد از رابطه یا از طرف مقابل
                - یک ماجرای مشخص اخیر و کاری که هر نفر در آن کرد (اگر گفته شده)
                - الگوی تکراری بینشان، بدون سرزنش (اگر دیده شد و اگر هر دو آن را شنیده‌اند)
                - ارزیابی هر نفر از اینکه رفتارش او را به خواسته‌اش نزدیک می‌کند یا نه (اگر گفته شده)
                - قدم کوچکی که قرار شد (اگر هست)
                - جلسه تا کجا جلو رفته و قدم بعدی منطقی چیست
                - هر موضوع حساس یا ایمنی که گفته شد (اگر هست)

                خلاصه‌ی قبلی:
                """ + (previous.isBlank() ? "(ندارد)" : previous) + """


                گفتگوی تازه:
                """ + transcript;

        var session = metisService.createConversation(insightBotId, user);
        String raw = metisService.sendMessage(session.getId(), prompt);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("empty summary");
        }
        String text = raw.trim();
        if (text.startsWith("{")) {
            // the bot answered in its JSON habit; not usable as a brief
            throw new IllegalStateException("summary came back as JSON");
        }
        if (text.length() > BRIEF_MAX_CHARS) {
            text = text.substring(0, BRIEF_MAX_CHARS);
        }
        return text;
    }

    private Map<Long, String> participantLabels(ChatRoom chatRoom) {
        Map<Long, String> labels = new HashMap<>();
        for (ChatRoomParticipant participant : participantRepository.findAllByChatRoom(chatRoom)) {
            if (participant.getUser() == null) {
                continue;
            }
            ParticipantRole role = participant.getRole();
            boolean owner = role == ParticipantRole.OWNER || role == ParticipantRole.PARTNER_A;
            labels.put(
                    participant.getUser().getId(),
                    "[" + (owner ? "OWNER" : "MEMBER") + " | " + participant.getUser().visibleName() + "]"
            );
        }
        return labels;
    }

    /** One transcript line, or null for messages that are not speech. */
    private String line(Message message, Map<Long, String> labels, int maxChars) {
        if (message.getSenderType() == MessageSenderType.SYSTEM || Boolean.TRUE.equals(message.getDeleted())) {
            return null;
        }
        String text = message.getContent();
        if (text == null) {
            return null;
        }
        if (CounselorSignal.isSticker(text)) {
            return null;
        }
        String visible = new GovernancePacketParser().strip(text).trim();
        if (visible.isBlank()) {
            return null;
        }
        if (visible.length() > maxChars) {
            visible = visible.substring(0, maxChars);
        }
        visible = visible.replace('\n', ' ');
        String who = message.getSenderType() == MessageSenderType.AGENT
                ? "COUNSELOR"
                : labels.getOrDefault(message.getSenderId(), "[PARTICIPANT]");
        return who + ": " + visible;
    }

    public record Brief(String summary, String block) {
    }
}
