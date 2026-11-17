package com.coupleai.coupleai.beinema.teach;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.counseling.TeachCardTeacher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The lesson cards of the teaching form: kept for the one person they were made for, shown only to
 * them, with a small question that checks the lesson was understood. The director is told, per
 * card, whether it was understood, so a lesson that did not land is explained again or its
 * prerequisite comes first.
 */
@Service
@RequiredArgsConstructor
public class TeachCardService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TeachCardRepository cards;
    private final ChatRoomRepository rooms;
    private final com.coupleai.coupleai.beinema.Repository.UserRepository users;

    /** A card for one person, from the teacher's fitted lesson. */
    @Transactional
    public TeachCard save(Long roomId, Long userId, String lessonId, String readiness, TeachCardTeacher.Fitted fitted) {
        TeachCard card = new TeachCard();
        card.setPublicId(UUID.randomUUID().toString());
        card.setChatRoomId(roomId);
        card.setUserId(userId);
        card.setLessonId(lessonId);
        card.setTitle(fitted.title());
        card.setReadiness(readiness);
        card.setContentJson(fitted.contentJson());
        card.setCheckAnswer(fitted.answer());
        card.setFitted(fitted.fitted());
        return cards.save(card);
    }

    /** One card for its owner: the content, and the answer only once they chose one. */
    @Transactional(readOnly = true)
    public Map<String, Object> view(String publicId, User user) {
        TeachCard card = owned(publicId, user);
        return view(card, locked(card, sameRoom(card)));
    }

    /** Their choice for the card's question: right or not, and the right option. */
    @Transactional
    public Map<String, Object> answer(String publicId, User user, int choice) {
        TeachCard card = owned(publicId, user);
        if (locked(card, sameRoom(card))) {
            throw new IllegalArgumentException("این درس بعد از نوشتن تمرین درس قبل باز می‌شود.");
        }
        JsonNode options = read(card).path("check").path("options");
        if (choice < 0 || choice >= options.size()) {
            throw new IllegalArgumentException("این گزینه وجود ندارد.");
        }
        card.setChosen(choice);
        card.setUnderstood(choice == card.getCheckAnswer());
        cards.save(card);
        return view(card, false);
    }

    /** What they wrote about doing the exercise of a lesson after a session: the exercise is done. */
    @Transactional
    public Map<String, Object> exercise(String publicId, User user, String note) {
        TeachCard card = owned(publicId, user);
        String text = note == null ? "" : note.trim();
        if (!card.afterSession() || !read(card).path("exercise").isObject()) {
            throw new IllegalArgumentException("این درس تمرینی ندارد.");
        }
        if (locked(card, sameRoom(card))) {
            throw new IllegalArgumentException("این درس بعد از نوشتن تمرین درس قبل باز می‌شود.");
        }
        if (text.length() < 10) {
            throw new IllegalArgumentException("چند جمله بنویس که چه کردی و چه دیدی.");
        }
        card.setExerciseNote(text.length() > 3000 ? text.substring(0, 3000) : text);
        if (card.getExerciseDoneAt() == null) {
            card.setExerciseDoneAt(java.time.Instant.now());
        }
        cards.save(card);
        return view(card, false);
    }

    /** Whether this person gets a lesson after each session. */
    @Transactional
    public void setAfterSession(User user, boolean on) {
        users.findById(user.getId()).ifPresent(found -> {
            found.setSessionLessonsOff(on ? null : Boolean.TRUE);
            users.save(found);
        });
    }

    /** Its owner opened it: no longer new on the dashboard. */
    @Transactional
    public void seen(String publicId, User user) {
        TeachCard card = owned(publicId, user);
        if (card.getSeenAt() == null) {
            card.setSeenAt(java.time.Instant.now());
            cards.save(card);
        }
    }

    /** The lessons after a session this person has not opened yet (two weeks): for the conversation cards of the dashboard. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> fresh(User user) {
        return cards.findAllByUserIdAndKindAndSeenAtIsNullAndCreatedAtAfter(user.getId(), TeachCard.SESSION,
                        java.time.LocalDateTime.now().minusDays(14)).stream()
                // a locked lesson is not new yet: it opens once the exercise before it is written
                .filter(card -> !locked(card, sameRoom(card)))
                .map(card -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("id", card.getPublicId());
                    item.put("chatRoomId", card.getChatRoomId());
                    item.put("title", card.getTitle());
                    return item;
                }).toList();
    }

    /** «درس‌های من»: every card of this person, newest first, with the conversation it came from. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(User user) {
        List<TeachCard> all = cards.findAllByUserIdOrderByIdDesc(user.getId());
        return all.stream().map(card -> {
            Map<String, Object> view = new java.util.LinkedHashMap<>(view(card, locked(card, all)));
            view.put("roomTitle", rooms.findById(card.getChatRoomId()).map(ChatRoom::getTitle).orElse(null));
            return view;
        }).toList();
    }

    /**
     * What the director reads about the cards of this room: per person, each lesson, whether its
     * question was answered and understood, and the choice when it was not.
     */
    @Transactional(readOnly = true)
    public String teachingBlock(Long roomId, Map<Long, String> roleByUserId) {
        StringBuilder out = new StringBuilder();
        for (TeachCard card : cards.findAllByChatRoomIdOrderByIdAsc(roomId)) {
            if (card.afterSession()) {
                continue;
            }
            String role = roleByUserId.getOrDefault(card.getUserId(), "?");
            out.append(card.getLessonId()).append(" | for ").append(role).append(" | readiness ")
                    .append(card.getReadiness()).append(" | ");
            if (card.getChosen() == null) {
                out.append("question NOT ANSWERED yet");
            } else if (Boolean.TRUE.equals(card.getUnderstood())) {
                out.append("UNDERSTOOD");
            } else {
                JsonNode options = read(card).path("check").path("options");
                out.append("NOT UNDERSTOOD: chose «").append(options.path(card.getChosen()).asText("")).append("» instead of «")
                        .append(options.path(card.getCheckAnswer()).asText("")).append('»');
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * What the director reads about the lessons after the sessions of this room: per person, the last two,
     * whether each is personal, and whether its exercise is done. What they wrote stays theirs.
     */
    @Transactional(readOnly = true)
    public String afterSessionBlock(Long roomId, Map<Long, String> roleByUserId) {
        List<TeachCard> given = cards.findAllByChatRoomIdAndKindOrderByIdAsc(roomId, TeachCard.SESSION);
        if (given.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("AFTER-SESSION LESSONS (each person's own: never tell one what the other got,"
                + " never test them; when a required exercise is still open you may once, early and kindly, ask that person"
                + " how trying it went):\n");
        Map<Long, List<TeachCard>> byUser = new java.util.LinkedHashMap<>();
        given.forEach(card -> byUser.computeIfAbsent(card.getUserId(), id -> new java.util.ArrayList<>()).add(card));
        byUser.forEach((userId, mine) -> mine.subList(Math.max(0, mine.size() - 2), mine.size()).forEach(card -> {
            JsonNode content = read(card);
            out.append(card.getLessonId()).append(" | for ").append(roleByUserId.getOrDefault(userId, "?"))
                    .append(" | after session ").append(card.getSessionNumber())
                    .append(content.path("personal").asBoolean(false) ? " | personal" : " | general")
                    .append(" | «").append(card.getTitle()).append("» | ");
            if (content.path("exercise").isObject()) {
                out.append(card.getExerciseDoneAt() != null ? "exercise DONE"
                        : Boolean.TRUE.equals(card.getExerciseRequired()) ? "required exercise NOT DONE yet" : "exercise not done");
            } else {
                out.append("no exercise");
            }
            out.append(card.getSeenAt() == null ? " | not opened yet" : "").append('\n');
        }));
        return out.toString();
    }

    /** A lesson after a session waits while an earlier one of the same conversation has a required exercise not written. */
    static boolean locked(TeachCard card, List<TeachCard> others) {
        if (!card.afterSession()) {
            return false;
        }
        return others.stream().anyMatch(other -> other.afterSession() && other.getId() < card.getId()
                && other.getChatRoomId().equals(card.getChatRoomId()) && other.getUserId().equals(card.getUserId())
                && other.exerciseOpen());
    }

    private List<TeachCard> sameRoom(TeachCard card) {
        return card.afterSession() ? cards.findAllByChatRoomIdAndKindOrderByIdAsc(card.getChatRoomId(), TeachCard.SESSION) : List.of();
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        cards.deleteAllByChatRoomId(roomId);
    }

    private TeachCard owned(String publicId, User user) {
        TeachCard card = cards.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("این کارت پیدا نشد."));
        if (!card.getUserId().equals(user.getId())) {
            // the card is only for the person it was made for, also inside a shared conversation
            throw new IllegalArgumentException("این کارت پیدا نشد.");
        }
        return card;
    }

    private Map<String, Object> view(TeachCard card, boolean locked) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", card.getPublicId());
        out.put("chatRoomId", card.getChatRoomId());
        out.put("lessonId", card.getLessonId());
        out.put("title", card.getTitle());
        out.put("kind", card.getKind());
        out.put("locked", locked);
        // a plain map: the web layer's JSON writer does not know this Jackson's tree nodes;
        // a locked lesson shows only its title until the exercise before it is written
        out.put("content", locked ? Map.of() : JSON.convertValue(read(card), Map.class));
        out.put("createdAt", card.getCreatedAt());
        if (card.afterSession()) {
            out.put("sessionNumber", card.getSessionNumber());
            out.put("exerciseRequired", Boolean.TRUE.equals(card.getExerciseRequired()));
            out.put("exerciseNote", card.getExerciseNote());
            out.put("exerciseDoneAt", card.getExerciseDoneAt());
            out.put("seen", card.getSeenAt() != null);
        }
        out.put("chosen", card.getChosen());
        out.put("understood", card.getUnderstood());
        if (card.getChosen() != null) {
            out.put("answer", card.getCheckAnswer());
        }
        // the sources travel inside the content (its practice), word for word from the lesson
        return out;
    }

    private static JsonNode read(TeachCard card) {
        try {
            return JSON.readTree(card.getContentJson());
        } catch (Exception broken) {
            return JSON.createObjectNode();
        }
    }
}
