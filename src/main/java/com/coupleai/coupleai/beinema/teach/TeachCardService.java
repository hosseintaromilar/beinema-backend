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
        return view(owned(publicId, user));
    }

    /** Their choice for the card's question: right or not, and the right option. */
    @Transactional
    public Map<String, Object> answer(String publicId, User user, int choice) {
        TeachCard card = owned(publicId, user);
        JsonNode options = read(card).path("check").path("options");
        if (choice < 0 || choice >= options.size()) {
            throw new IllegalArgumentException("این گزینه وجود ندارد.");
        }
        card.setChosen(choice);
        card.setUnderstood(choice == card.getCheckAnswer());
        cards.save(card);
        return view(card);
    }

    /** «درس‌های من»: every card of this person, newest first, with the conversation it came from. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(User user) {
        return cards.findAllByUserIdOrderByIdDesc(user.getId()).stream().map(card -> {
            Map<String, Object> view = new java.util.LinkedHashMap<>(view(card));
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

    private Map<String, Object> view(TeachCard card) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", card.getPublicId());
        out.put("chatRoomId", card.getChatRoomId());
        out.put("lessonId", card.getLessonId());
        out.put("title", card.getTitle());
        // a plain map: the web layer's JSON writer does not know this Jackson's tree nodes
        out.put("content", JSON.convertValue(read(card), Map.class));
        out.put("createdAt", card.getCreatedAt());
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
