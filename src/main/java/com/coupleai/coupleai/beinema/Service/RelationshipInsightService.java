package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.InsightLlmPayload;
import com.coupleai.coupleai.beinema.DTO.ChatRoom.RelationshipInsightResponse;
import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomAgent;
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

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final MessageRepository messageRepository;
    private final RelationshipInsightRepository insightRepository;
    private final MetisService metisService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RelationshipInsightResponse getInsight(String email, Long chatRoomId) {
        ChatRoom chatRoom = requireAccessibleRoom(email, chatRoomId);
        return insightRepository.findByChatRoom(chatRoom)
                .map(this::toResponse)
                .orElseGet(() -> emptyResponse(
                        chatRoomId,
                        "هنوز مسیر رابطه برای این گفتگو ساخته نشده است."
                ));
    }

    @Transactional
    public RelationshipInsightResponse refreshInsight(String email, Long chatRoomId) {
        User user = requireUser(email);
        ChatRoom chatRoom = requireAccessibleRoom(user, chatRoomId);

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
                        "ایجنت این گفتگو پیدا نشد."
                ));

        String transcript = buildTranscript(chatRoom, messages);
        String prompt = buildPrompt(transcript);

        try {
            MetisConversationResponse session = metisService.createConversation(
                    roomAgent.getAgent().getAiBotId(),
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
            return toResponse(insightRepository.save(insight));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "تحلیل مسیر رابطه الان ممکن نشد. کمی بعد دوباره تلاش کنید."
            );
        }
    }

    private RelationshipInsightResponse toResponse(RelationshipInsight insight) {
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
                        ? "هنوز مسیر رابطه برای این گفتگو ساخته نشده است."
                        : null)
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
                        participant -> participant.getUser().getName(),
                        (left, right) -> left
                ));

        List<Message> recent = messages.size() > MAX_MESSAGES
                ? messages.subList(messages.size() - MAX_MESSAGES, messages.size())
                : messages;

        StringBuilder transcript = new StringBuilder();
        for (Message message : recent) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String speaker;
            if (message.getSenderType() == MessageSenderType.USER) {
                speaker = names.getOrDefault(message.getSenderId(), "شریک");
            } else if (message.getSenderType() == MessageSenderType.AGENT) {
                speaker = "ایجنت";
            } else {
                speaker = "سیستم";
            }
            transcript.append(speaker)
                    .append(": ")
                    .append(trimTo(message.getContent().replace('\n', ' '), MAX_MESSAGE_CHARS))
                    .append('\n');
        }
        return transcript.toString();
    }

    private String buildPrompt(String transcript) {
        return """
                تو آینهٔ رابطه هستی، نه قاضی و نه درمانگر.
                طرف رابطه را بگیر، نه افراد را. مقصر مشخص نکن. نمره، درصد، لیگ یا برچسب بالینی نده.
                از گفتگوی مشترک زیر، فقط JSON معتبر برگردان. هیچ متن دیگری ننویس.

                کلیدها:
                whereWeWere: یک یا دو جمله درباره الگوی قبلی رابطه به زبان «ما»
                whereWeAre: یک یا دو جمله درباره وضعیت فعلی به زبان «ما»
                status: فقط یکی از starting, softer, same, returned
                needs: آرایه حداکثر ۳ نیاز رابطه مثل شنیده شدن، امنیت، احترام
                patterns: آرایه حداکثر ۳ الگوی تکراری بدون نام بردن مقصر
                nextStep: یک قدم کوچک برای ۷ روز آینده که هر دو بتوانند انجام دهند
                dailySentence: یک جمله کوتاه، گرم و غیرآمرانه برای امروز این رابطه

                گفتگو:
                """ + transcript;
    }

    private InsightLlmPayload parsePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("پاسخ تحلیل خالی بود.");
        }
        String json = extractJson(raw);
        try {
            InsightLlmPayload payload = objectMapper.readValue(json, InsightLlmPayload.class);
            if (payload == null || isBlank(payload.getWhereWeAre()) && isBlank(payload.getDailySentence())) {
                throw new IllegalArgumentException("تحلیل قابل فهم نبود.");
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("تحلیل قابل فهم نبود.");
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
