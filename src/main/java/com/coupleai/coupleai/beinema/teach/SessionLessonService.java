package com.coupleai.coupleai.beinema.teach;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Enum.IntentEpistemic;
import com.coupleai.coupleai.beinema.Enum.IntentStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.ClientIntentRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.counseling.CounselingSnapshot;
import com.coupleai.coupleai.beinema.counseling.CounselingStateStore;
import com.coupleai.coupleai.beinema.counseling.SessionLessons;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One lesson for each person after every session that had a conversation, kept only for them
 * (docs/architecture/personal-lessons.md):
 * <ul>
 *   <li>personal, when in that session they confirmed a reading about themselves and one of their
 *   companion's lessons truly fits it: the writer fits it to them;</li>
 *   <li>else general: the next lesson of their companion not given to them yet.</li>
 * </ul>
 * None for someone who did not take part in the session or turned lessons off, and none at all when
 * the conversation shows a safety concern. A lesson may carry an exercise; a required one keeps the
 * next lesson of the conversation locked until it is written (TeachCardService).
 */
@Service
@RequiredArgsConstructor
public class SessionLessonService {

    private static final Logger log = LoggerFactory.getLogger(SessionLessonService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String SCHEMA = "session-lesson.v1";

    static final Map<AgentType, String> COMPANIONS = Map.of(
            AgentType.RELATIONSHIP, "نینا، همراه رابطهٔ زوج‌ها",
            AgentType.FINANCIAL, "پوریا، همراه گفتگوی مالی زوج‌ها",
            AgentType.PARENTING, "رها، همراه والدین در تربیت فرزند",
            AgentType.PREMARITAL, "الیکا، همراه گفتگوهای پیش از ازدواج",
            AgentType.IN_LAWS, "نادر، همراه رابطه با خانواده‌ها",
            AgentType.SEXOLOGY, "راحله، همراه صمیمیت و رابطهٔ زناشویی");

    private final TeachCardRepository cards;
    private final ChatRoomRepository rooms;
    private final ChatRoomParticipantRepository participants;
    private final ChatRoomAgentRepository roomAgents;
    private final AgentRepository agents;
    private final ClientIntentRepository intents;
    private final MessageRepository messages;
    private final CounselingStateStore stateStore;
    private final SessionLessonWriter writer;

    /** After a session closed (in the background of its closing): never throws. */
    public void afterSession(Long roomId, int session, int startSequence, Instant startedAt) {
        try {
            make(roomId, session, startSequence, startedAt);
        } catch (RuntimeException failure) {
            log.warn("Lessons after session {} of room {} failed: {}", session, roomId, failure.getMessage());
        }
    }

    void make(Long roomId, int session, int startSequence, Instant startedAt) {
        ChatRoom room = rooms.findById(roomId).orElse(null);
        if (room == null) {
            return;
        }
        AgentType companion = roomAgents.findActiveAgentIds(roomId).stream().findFirst()
                .flatMap(agents::findById).map(Agent::getType).orElse(null);
        List<SessionLessons.Lesson> library = companion == null ? List.of() : SessionLessons.of(companion);
        if (library.isEmpty()) {
            return;
        }
        CounselingSnapshot snapshot = CounselingStateStore.snapshot(stateStore.load(roomId));
        if (snapshot.safety != null && snapshot.safety.level != null && !"NONE".equals(snapshot.safety.level)) {
            log.info("No lessons after session {} of room {}: a safety concern", session, roomId);
            return;
        }
        List<ClientIntent> readings = intents.findAllByChatRoomIdOrderByIdAsc(roomId);
        for (ChatRoomParticipant participant : participants.findAllByChatRoom(room)) {
            User user = participant.getUser();
            if (user == null || user.isDeleted() || Boolean.TRUE.equals(user.getSessionLessonsOff())
                    || cards.existsByChatRoomIdAndUserIdAndKindAndSessionNumber(roomId, user.getId(), TeachCard.SESSION, session)
                    || !messages.existsByChatRoomIdAndSenderTypeAndSenderIdAndDeletedFalseAndSequenceNumberGreaterThanEqual(
                    roomId, MessageSenderType.USER, user.getId(), startSequence)) {
                continue;
            }
            TeachCard card = lessonFor(user, roomId, session, companion, library,
                    confirmed(readings, user.getId(), session, startedAt));
            if (card != null) {
                cards.save(card);
                log.info("Lesson {} after session {} of room {} for user {} ({})", card.getLessonId(), session, roomId,
                        user.getId(), card.isFitted() ? "personal" : "general");
            }
        }
    }

    /** Their lesson for this session: personal when a confirmed reading meets a lesson, else the next general one. */
    TeachCard lessonFor(User user, Long roomId, int session, AgentType companion, List<SessionLessons.Lesson> library,
                        List<String> confirmed) {
        Set<String> given = cards.findAllByUserIdAndKindOrderByIdAsc(user.getId(), TeachCard.SESSION).stream()
                .map(TeachCard::getLessonId).collect(Collectors.toSet());
        if (!confirmed.isEmpty()) {
            List<SessionLessons.Lesson> fresh = library.stream().filter(lesson -> !given.contains(lesson.id())).toList();
            // a new reading may bring back a lesson already given: it is then fitted to what is new
            SessionLessonWriter.Written written = writer.write(COMPANIONS.get(companion), firstName(user), confirmed,
                    fresh.isEmpty() ? library : fresh);
            if (written != null) {
                SessionLessons.Lesson lesson = SessionLessons.find(written.lessonId()).orElse(null);
                if (lesson != null) {
                    return card(user, roomId, session, lesson, companion, written);
                }
            }
        }
        return library.stream().filter(lesson -> !given.contains(lesson.id())).findFirst()
                .map(lesson -> card(user, roomId, session, lesson, companion, null))
                .orElse(null);
    }

    /** What this person confirmed about themselves in this session (our readings, not their own words). */
    static List<String> confirmed(List<ClientIntent> all, Long userId, int session, Instant startedAt) {
        java.time.LocalDateTime since = startedAt == null ? null : java.time.LocalDateTime.ofInstant(startedAt, ZoneId.systemDefault());
        return all.stream()
                .filter(intent -> userId.equals(intent.getParticipantUserId()))
                .filter(intent -> intent.getStatus() == IntentStatus.CONFIRMED
                        && intent.getEpistemic() == IntentEpistemic.CONFIRMED_BY_CLIENT)
                .filter(intent -> Integer.valueOf(session).equals(intent.getSessionNumber())
                        || (since != null && intent.getUpdatedAt() != null && !intent.getUpdatedAt().isBefore(since)))
                .map(intent -> intent.getInterpretation() == null || intent.getInterpretation().isBlank()
                        ? intent.getQuote() : intent.getInterpretation())
                .filter(text -> text != null && !text.isBlank())
                .map(text -> text.length() > 400 ? text.substring(0, 400) : text)
                .limit(6)
                .toList();
    }

    static TeachCard card(User user, Long roomId, int session, SessionLessons.Lesson lesson, AgentType companion,
                          SessionLessonWriter.Written written) {
        ObjectNode content = JSON.createObjectNode();
        content.put("schema", SCHEMA);
        content.put("personal", written != null);
        content.put("companion", COMPANIONS.getOrDefault(companion, ""));
        String title = written == null ? lesson.faTitle() : written.title();
        content.put("title", title);
        content.put("intro", lesson.intro());
        content.put("seen", written == null ? "" : written.seen());
        ArrayNode body = content.putArray("body");
        (written == null ? lesson.body() : written.body()).forEach(body::add);
        content.put("example", written == null || written.example().isBlank() ? lesson.example() : written.example());
        content.put("step", written == null ? lesson.step() : written.step());
        if (lesson.exercise() != null) {
            ObjectNode exercise = content.putObject("exercise");
            exercise.put("required", lesson.exercise().required());
            exercise.put("task", written == null || written.exerciseTask().isBlank() ? lesson.exercise().task() : written.exerciseTask());
            exercise.put("prompt", written == null || written.exercisePrompt().isBlank() ? lesson.exercise().prompt() : written.exercisePrompt());
        }
        // the small question is the lesson's own, word for word: its right option is known
        ObjectNode check = content.putObject("check");
        check.put("question", lesson.check().question());
        ArrayNode options = check.putArray("options");
        lesson.check().options().forEach(options::add);

        TeachCard card = new TeachCard();
        card.setPublicId(UUID.randomUUID().toString());
        card.setChatRoomId(roomId);
        card.setUserId(user.getId());
        card.setLessonId(lesson.id());
        card.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        card.setKind(TeachCard.SESSION);
        card.setSessionNumber(session);
        try {
            card.setContentJson(JSON.writeValueAsString(content));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
        card.setCheckAnswer(lesson.check().answer());
        card.setFitted(written != null);
        card.setExerciseRequired(lesson.exercise() != null && lesson.exercise().required());
        return card;
    }

    private static String firstName(User user) {
        String name = user.getFirstName() != null && !user.getFirstName().isBlank() ? user.getFirstName() : user.getName();
        return name == null ? "" : name.trim();
    }
}
