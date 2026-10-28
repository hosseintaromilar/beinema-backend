package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.InsightLlmPayload;
import com.coupleai.coupleai.beinema.DTO.ChatRoom.RelationshipInsightResponse;
import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomAgent;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.RelationshipInsight;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.RelationshipInsightRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelationshipInsightService {

    private static final int MAX_MESSAGES = 80;
    private static final int MAX_MESSAGE_CHARS = 420;
    private static final int MIN_USER_MESSAGES = 2;
    private static final Logger log = LoggerFactory.getLogger(RelationshipInsightService.class);

    @Value("${metis.insight-bot-id}")
    private String insightBotId;

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final MessageRepository messageRepository;
    private final RelationshipInsightRepository insightRepository;
    private final MetisService metisService;
    private final ObjectMapper objectMapper;
    private final ChatRoomEventHub eventHub;

    @Transactional(readOnly = true)
    public RelationshipInsightResponse getInsight(String email, Long chatRoomId) {
        User user = requireUser(email);
        ChatRoom chatRoom = requireAccessibleRoom(user, chatRoomId);
        return insightRepository.findByChatRoom(chatRoom)
                .map(insight -> toResponse(insight, isUnread(insight, chatRoom, user)))
                .orElseGet(() -> emptyResponse(
                        chatRoomId,
                        "مسیر رابطه‌تان بعد از پایان اولین جلسه اینجا نوشته می‌شود."
                ));
    }

    /** They opened the path: its «1» goes away. */
    @Transactional
    public void markSeen(String email, Long chatRoomId) {
        User user = requireUser(email);
        ChatRoom chatRoom = requireAccessibleRoom(user, chatRoomId);
        participantRepository.findByChatRoomAndUser(chatRoom, user).ifPresent(person -> {
            person.setInsightSeenAt(LocalDateTime.now());
            participantRepository.save(person);
        });
    }

    /** A path written after this person last opened it (the badge on the room and its card). */
    @Transactional(readOnly = true)
    public boolean unreadFor(ChatRoom chatRoom, User user) {
        if (chatRoom == null || user == null) {
            return false;
        }
        return insightRepository.findByChatRoom(chatRoom)
                .map(insight -> isUnread(insight, chatRoom, user))
                .orElse(false);
    }

    private boolean isUnread(RelationshipInsight insight, ChatRoom chatRoom, User user) {
        if (insight.getAnalyzedAt() == null) {
            return false;
        }
        LocalDateTime seen = participantRepository.findByChatRoomAndUser(chatRoom, user)
                .map(ChatRoomParticipant::getInsightSeenAt)
                .orElse(null);
        return seen == null || seen.isBefore(insight.getAnalyzedAt());
    }

    @Transactional
    public RelationshipInsightResponse refreshInsight(String email, Long chatRoomId) {
        User user = requireUser(email);
        ChatRoom chatRoom = requireAccessibleRoom(user, chatRoomId);
        return refresh(user, chatRoom);
    }

    /**
     * Writes the path again when a session closes, so it is ready without anyone
     * asking; the open pages hear «insight_updated» and show it. Never throws:
     * a failed path must not stop the session from closing.
     */
    @Transactional
    public void refreshAfterSession(Long chatRoomId, User user) {
        try {
            ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
            if (chatRoom == null) {
                return;
            }
            User asker = user != null ? user : participantRepository.findAllByChatRoom(chatRoom).stream()
                    .map(ChatRoomParticipant::getUser)
                    .findFirst()
                    .orElse(null);
            if (asker == null) {
                return;
            }
            RelationshipInsightResponse fresh = refresh(asker, chatRoom);
            if (!fresh.isEmpty()) {
                eventHub.publishNamed(chatRoomId, "insight_updated",
                        Map.of("chatRoomId", chatRoomId, "analyzedAt", fresh.getAnalyzedAt()));
            }
        } catch (RuntimeException exception) {
            log.warn("Could not write the relationship path of room {}: {}", chatRoomId, exception.getMessage());
        }
    }

    private RelationshipInsightResponse refresh(User user, ChatRoom chatRoom) {
        Long chatRoomId = chatRoom.getId();
        List<Message> messages = messageRepository
                .findByChatRoomIdAndDeletedFalseOrderBySequenceNumberAsc(chatRoomId);

        long userMessageCount = messages.stream()
                .filter(message -> message.getSenderType() == MessageSenderType.USER)
                .count();

        if (userMessageCount < MIN_USER_MESSAGES) {
            RelationshipInsight empty = insightRepository.findByChatRoom(chatRoom)
                    .orElseGet(() -> RelationshipInsight.builder()
                            .chatRoom(chatRoom)
                            .build());
            empty.setWhereWeWere(null);
            empty.setWhereWeAre(null);
            empty.setStatus("starting");
            empty.setNeedsJson("[]");
            empty.setPatternsJson("[]");
            empty.setNextStep(null);
            empty.setDailySentence(null);
            empty.setAnalyzedAt(null);
            empty.setMessageCountAtAnalysis(messages.size());
            insightRepository.save(empty);
            return emptyResponse(
                    chatRoomId,
                    "برای دیدن مسیر رابطه، کمی بیشتر در این فضای مشترک گفتگو کنید."
            );
        }

        ChatRoomAgent roomAgent = chatRoomAgentRepository.findAllByChatRoom(chatRoom)
                .stream()
                .filter(link -> Boolean.TRUE.equals(link.getActive()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "مشاور این گفتگو پیدا نشد."
                ));

        String transcript = buildTranscript(chatRoom, messages);
        RelationshipInsight previous = insightRepository.findByChatRoom(chatRoom)
                .filter(earlier -> earlier.getAnalyzedAt() != null && !isBlank(earlier.getWhereWeAre()))
                .orElse(null);
        String prompt = buildPrompt(transcript, previous == null ? null : previous.getWhereWeAre());

        try {
            MetisConversationResponse session = metisService.createConversation(
                    insightBotId,
                    user
            );
            String raw = metisService.sendMessage(session.getId(), prompt);
            InsightLlmPayload payload = parsePayload(raw);

            RelationshipInsight insight = insightRepository.findByChatRoom(chatRoom)
                    .orElseGet(() -> RelationshipInsight.builder()
                            .chatRoom(chatRoom)
                            .build());
            insight.setWhereWeWere(trimTo(payload.getWhereWeWere(), 800));
            insight.setWhereWeAre(trimTo(payload.getWhereWeAre(), 800));
            insight.setStatus(normalizeStatus(payload.getStatus()));
            insight.setNeedsJson(writeList(payload.getNeeds()));
            insight.setPatternsJson(writeList(payload.getPatterns()));
            insight.setNextStep(trimTo(payload.getNextStep(), 500));
            insight.setDailySentence(trimTo(payload.getDailySentence(), 280));
            insight.setAnalyzedAt(LocalDateTime.now());
            insight.setMessageCountAtAnalysis(messages.size());
            insight.setAnalysisSessionId(session.getId());
            return toResponse(insightRepository.save(insight), false);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "نوشتن مسیر رابطه الان ممکن نشد. کمی بعد دوباره سر بزنید."
            );
        }
    }

    private RelationshipInsightResponse toResponse(RelationshipInsight insight, boolean unread) {
        boolean empty = insight.getAnalyzedAt() == null
                && isBlank(insight.getWhereWeAre())
                && isBlank(insight.getDailySentence());
        return RelationshipInsightResponse.builder()
                .chatRoomId(insight.getChatRoom().getId())
                .empty(empty)
                .whereWeWere(insight.getWhereWeWere())
                .whereWeAre(insight.getWhereWeAre())
                .status(insight.getStatus())
                .needs(readList(insight.getNeedsJson()))
                .patterns(readList(insight.getPatternsJson()))
                .nextStep(insight.getNextStep())
                .dailySentence(insight.getDailySentence())
                .analyzedAt(toEpochMillis(insight.getAnalyzedAt()))
                .message(empty
                        ? "مسیر رابطه‌تان بعد از پایان اولین جلسه اینجا نوشته می‌شود."
                        : null)
                .unread(unread)
                .build();
    }

    private RelationshipInsightResponse emptyResponse(Long chatRoomId, String message) {
        return RelationshipInsightResponse.builder()
                .chatRoomId(chatRoomId)
                .empty(true)
                .needs(List.of())
                .patterns(List.of())
                .message(message)
                .build();
    }

    private String buildTranscript(ChatRoom chatRoom, List<Message> messages) {
        Map<Long, String> names = participantRepository.findAllByChatRoom(chatRoom)
                .stream()
                .collect(Collectors.toMap(
                        participant -> participant.getUser().getId(),
                        participant -> participant.getUser().visibleName(),
                        (left, right) -> left
                ));

        List<Message> recent = messages.size() > MAX_MESSAGES
                ? messages.subList(messages.size() - MAX_MESSAGES, messages.size())
                : messages;

        StringBuilder transcript = new StringBuilder();
        for (Message message : recent) {
            // only what the two people and the companion said; system notices are noise here
            if (message.getContent() == null || message.getContent().isBlank()
                    || message.getVisibleToUserId() != null
                    || (message.getSenderType() != MessageSenderType.USER
                        && message.getSenderType() != MessageSenderType.AGENT)) {
                continue;
            }
            String speaker = message.getSenderType() == MessageSenderType.USER
                    ? names.getOrDefault(message.getSenderId(), "شریک")
                    : "همراه";
            transcript.append(speaker)
                    .append(": ")
                    .append(trimTo(com.coupleai.coupleai.beinema.counseling.MessageTexts.visible(message).replace('\n', ' '), MAX_MESSAGE_CHARS))
                    .append('\n');
        }
        return transcript.toString();
    }

    /**
     * The path is a plain summary of this very conversation, read by both
     * people: nothing guessed, nothing from outside it, empty where the
     * conversation says nothing.
     */
    private String buildPrompt(String transcript, String lastSummary) {
        String before = lastSummary == null
                ? """
                  این اولین جمع‌بندی این گفتگوست: whereWeWere را "" بگذار و status را starting.
                  """
                : "جمع‌بندی جلسهٔ قبل (whereWeWere را از همین بنویس و status را با مقایسه با آن انتخاب کن):\n"
                  + lastSummary + "\n";
        return """
                تو جمع‌بندی‌نویس گفتگوی دو نفر در یک رابطه هستی. خروجی تو را هر دو نفر می‌خوانند.

                قواعد:
                - فقط از گفتگوی زیر استفاده کن. چیزی را که در گفتگو گفته نشده حدس نزن و از خودت اضافه نکن.
                - اگر برای کلیدی چیزی در گفتگو نیست، آن را خالی بگذار ("" یا []).
                - طرف رابطه باش، نه یکی از دو نفر. کسی را مقصر نکن. نمره، درصد یا برچسب روان‌شناختی نده.
                - فارسی ساده و روزمره، با زبان «ما». بدون اصطلاح تخصصی، شعر، استعاره یا جملهٔ کلیشه‌ای.
                - هر جمله کوتاه باشد و به موضوع مشخص همین گفتگو اشاره کند.
                - فقط JSON معتبر برگردان، بدون هیچ متن دیگری.

                کلیدها:
                whereWeWere: یک جمله دربارهٔ جلسهٔ قبل، یا ""
                whereWeAre: یک یا دو جمله: در این جلسه دربارهٔ چه حرف زدیم و به کجا رسیدیم
                status: یکی از starting (اولین جلسه)، softer (لحن آرام‌تر شد یا به تفاهم نزدیک‌تر شدیم)، same (بحث همان‌جا ماند)، returned (مشکلی که بهتر شده بود دوباره پیش آمد)
                needs: حداکثر ۳ نیازی که خود ما در گفتگو گفتیم، هر کدام در چند کلمه
                patterns: حداکثر ۳ الگوی تکراری که در همین گفتگو روشن دیده شد، بدون نام بردن از کسی؛ اگر روشن نیست []
                nextStep: قدمی که در جلسه رویش توافق کردیم؛ اگر توافقی نشد، یک قدم کوچک و عملی که مستقیم از موضوع همین گفتگو بیاید
                dailySentence: یک جملهٔ کوتاه که نکتهٔ اصلی همین جلسه را یادمان بیاورد

                """ + before + """

                گفتگو:
                """ + transcript;
    }

    private InsightLlmPayload parsePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("نوشتن مسیر رابطه الان ممکن نشد. کمی بعد دوباره سر بزنید.");
        }
        String json = extractJson(raw);
        try {
            InsightLlmPayload payload = objectMapper.readValue(json, InsightLlmPayload.class);
            if (payload == null || isBlank(payload.getWhereWeAre()) && isBlank(payload.getDailySentence())) {
                throw new IllegalArgumentException("نوشتن مسیر رابطه الان ممکن نشد. کمی بعد دوباره سر بزنید.");
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("نوشتن مسیر رابطه الان ممکن نشد. کمی بعد دوباره سر بزنید.");
        }
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "same";
        }
        String normalized = status.trim().toLowerCase();
        if (normalized.equals("starting")
                || normalized.equals("softer")
                || normalized.equals("same")
                || normalized.equals("returned")) {
            return normalized;
        }
        return "same";
    }

    private String writeList(List<String> values) {
        List<String> clean = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    clean.add(trimTo(value.trim(), 80));
                    if (clean.size() == 3) {
                        break;
                    }
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(clean);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {});
            return values == null ? List.of() : values;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private ChatRoom requireAccessibleRoom(String email, Long chatRoomId) {
        return requireAccessibleRoom(requireUser(email), chatRoomId);
    }

    private ChatRoom requireAccessibleRoom(User user, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("گفتگو پیدا نشد."));
        if (!participantRepository.existsByChatRoomAndUser(chatRoom, user)) {
            throw new IllegalArgumentException("شما عضو این گفتگو نیستید.");
        }
        return chatRoom;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("کاربر پیدا نشد."));
    }

    private static Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
