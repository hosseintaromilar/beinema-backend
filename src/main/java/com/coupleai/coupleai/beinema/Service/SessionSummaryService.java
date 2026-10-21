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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the private brief that is saved when a session ends and that the next
 * session starts from. It only prepares and carries the brief; reading it and
 * deciding what to do next stays in the workflow.
 */
@Service
@RequiredArgsConstructor
public class SessionSummaryService {

    private static final int BRIEF_MAX_CHARS = 2600;
    private static final int RECENT_MESSAGE_CHARS = 280;
    private static final int SUMMARY_INPUT_CHARS = 14000;

    @Value("${beinema.session.keep-recent:6}")
    private int keepRecent;

    @Value("${metis.insight-bot-id}")
    private String insightBotId;

    private final MessageRepository messageRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MetisService metisService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public int currentSequence(ChatRoom chatRoom) {
        return messageRepository.findMaxSequenceNumberByChatRoom(chatRoom).orElse(0);
    }

    /**
     * Summarises what was said after {@code startSequence}, building on the
     * previous summary. Falls back to a plain excerpt when the summariser is
     * not available, so ending a session never fails because of it.
     */
    public String summarise(ChatRoom chatRoom, User user, int startSequence, String previousSummary) {
        Map<Long, String> labels = participantLabels(chatRoom);
        List<String> lines = new ArrayList<>();
        for (Message message : messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom)) {
            if (message.getSequenceNumber() != null && message.getSequenceNumber() <= startSequence) {
                continue;
            }
            String line = line(message, labels, 600);
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return previousSummary == null ? "" : previousSummary;
        }
        String transcript = String.join("\n", lines);
        if (transcript.length() > SUMMARY_INPUT_CHARS) {
            transcript = transcript.substring(transcript.length() - SUMMARY_INPUT_CHARS);
        }
        try {
            return ask(previousSummary == null ? "" : previousSummary, transcript, user);
        } catch (Exception exception) {
            return fallback(previousSummary, lines);
        }
    }

    /**
     * One short private summary per person: what they said they want, what worries
     * them, what they described doing and any step they accepted. Only from that
     * person's own words. Returns what could be read; people without a usable
     * answer are left out (the room summary still covers them).
     */
    public Map<Long, String> summariseParticipants(ChatRoom chatRoom, User user, int startSequence) {
        List<ChatRoomParticipant> people = participantRepository.findAllByChatRoom(chatRoom);
        Map<String, Long> idByName = new LinkedHashMap<>();
        for (ChatRoomParticipant person : people) {
            if (person.getUser() != null && person.getUser().visibleName() != null) {
                idByName.put(person.getUser().visibleName().trim(), person.getUser().getId());
            }
        }
        if (idByName.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> labels = participantLabels(chatRoom);
        List<String> lines = new ArrayList<>();
        for (Message message : messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom)) {
            if (message.getSequenceNumber() != null && message.getSequenceNumber() <= startSequence) {
                continue;
            }
            String line = line(message, labels, 600);
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return Map.of();
        }
        String transcript = String.join("\n", lines);
        if (transcript.length() > SUMMARY_INPUT_CHARS) {
            transcript = transcript.substring(transcript.length() - SUMMARY_INPUT_CHARS);
        }

        String prompt = """
                تو دستیار یادداشت‌بردار یک جلسه‌ی مشاوره‌ی زوج هستی. برای هر نفر یک خلاصه‌ی خصوصی و کوتاه بنویس (حداکثر چهار جمله).
                فقط از حرف‌های خود همان نفر در این جلسه: چه می‌خواهد، چه چیزی نگرانش می‌کند، چه رفتاری از خودش توصیف کرد، و اگر قدمی را پذیرفت یا قرار شد.
                حدس نزن و از زبان یک نفر درباره‌ی نفر دیگر ننویس. اگر کسی چیزی نگفته بود، برایش خلاصه‌ی خالی ("") بگذار.
                فقط یک JSON معتبر برگردان که کلیدش دقیقاً نام هر نفر است و مقدارش خلاصه‌ی او؛ هیچ متن دیگری ننویس.

                نام‌ها: """ + String.join("، ", idByName.keySet()) + """


                گفتگو:
                """ + transcript;

        var session = metisService.createConversation(insightBotId, user);
        String raw = metisService.sendMessage(session.getId(), prompt);
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        int from = raw.indexOf('{');
        int to = raw.lastIndexOf('}');
        if (from < 0 || to <= from) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    raw.substring(from, to + 1),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Map<Long, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : idByName.entrySet()) {
                Object value = parsed.get(entry.getKey());
                if (value instanceof String text && !text.isBlank()) {
                    result.put(entry.getValue(), text.trim().length() > 700
                            ? text.trim().substring(0, 700)
                            : text.trim());
                }
            }
            return result;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    /** The block that travels with the first message of the next session. */
    public String resumeBlock(ChatRoom chatRoom, String summary) {
        Map<Long, String> labels = participantLabels(chatRoom);
        List<String> recent = new ArrayList<>();
        for (Message message : messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom)) {
            String line = line(message, labels, RECENT_MESSAGE_CHARS);
            if (line != null) {
                recent.add(line);
            }
        }
        int from = Math.max(0, recent.size() - keepRecent);
        return "[SYSTEM | SESSION]\n"
                + "EVENT=SESSION_CONTINUED\n"
                + "This is the same counseling process; the couple took a break and a new session begins now. "
                + "The block below is private background for the counselor. Never mention it to the couple.\n"
                + "BRIEF_SO_FAR:\n" + summary + "\n"
                + "RECENT_MESSAGES:\n" + String.join("\n", recent.subList(from, recent.size())) + "\n"
                + "END_OF_BRIEF\n";
    }

    private String ask(String previous, String transcript, User user) {
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
        return text.length() > BRIEF_MAX_CHARS ? text.substring(0, BRIEF_MAX_CHARS) : text;
    }

    private String fallback(String previous, List<String> lines) {
        int from = Math.max(0, lines.size() - 12);
        String excerpt = String.join("\n", lines.subList(from, lines.size()));
        String head = previous == null || previous.isBlank() ? "" : previous + "\n\n";
        String text = head + "آخرین بخش جلسه (خلاصه‌ساز در دسترس نبود):\n" + excerpt;
        return text.length() > BRIEF_MAX_CHARS ? text.substring(text.length() - BRIEF_MAX_CHARS) : text;
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
        if (text == null || CounselorSignal.isSticker(text)) {
            return null;
        }
        String visible = com.coupleai.coupleai.beinema.counseling.MessageTexts.visible(message).trim();
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
}
