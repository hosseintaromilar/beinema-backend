package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Exception.ChatRoomAccessDeniedException;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.blog.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Something to read during a break: the lessons this room was already taught, and a few blog posts that fit
 * what the room is about (its title, the goal and thread of the work, and the companion's field). Nothing is
 * stored and nothing of the conversation leaves the room: the posts are only ranked by these words.
 */
@Service
@RequiredArgsConstructor
public class BreakReadingService {

    static final int MAX_POSTS = 3;
    private static final int MIN_WORD = 3;

    private static final Map<AgentType, String> FIELD_WORDS = Map.of(
            AgentType.RELATIONSHIP, "رابطه زوج همسر دعوا گفتگو ارتباط اختلاف",
            AgentType.FINANCIAL, "مالی پول بودجه خرج پس‌انداز درآمد بدهی",
            AgentType.PARENTING, "فرزند کودک بچه تربیت والدین نوجوان",
            AgentType.PREMARITAL, "ازدواج خواستگاری نامزد آشنایی",
            AgentType.IN_LAWS, "خانواده مادرشوهر مادرزن والدین خانواده‌ها",
            AgentType.SEXOLOGY, "صمیمیت جنسی نزدیکی رابطه");

    private static final Set<String> COMMON = Set.of(
            "این", "اون", "برای", "که", "با", "از", "در", "به", "را", "رو", "های", "هایی", "است", "یک", "یه",
            "می", "هم", "تا", "اما", "ولی", "چه", "چی", "خود", "خودم", "خودت", "شما", "ما", "من", "تو");

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final AgentRepository agentRepository;
    private final CounselingStateStore stateStore;
    private final BlogService blogService;

    @Transactional(readOnly = true)
    public Map<String, Object> forUser(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        if (user == null || !participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new ChatRoomAccessDeniedException("Access denied");
        }
        CounselingSnapshot snapshot = CounselingStateStore.snapshot(stateStore.load(roomId));

        List<Map<String, Object>> lessons = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        StringBuilder about = new StringBuilder(room.getTitle() == null ? "" : room.getTitle())
                .append(' ').append(snapshot.goal).append(' ').append(snapshot.thread);
        for (CounselingSnapshot.Taught taught : snapshot.lessons) {
            if (taught.id == null || !seen.add(taught.id)) {
                continue;
            }
            Lessons.find(taught.id).ifPresent(lesson -> {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("id", lesson.id());
                card.put("title", lesson.faTitle());
                card.put("points", lesson.core());
                card.put("example", lesson.example());
                lessons.add(card);
                about.append(' ').append(lesson.faTitle());
            });
        }

        String field = chatRoomAgentRepository.findActiveAgentIds(roomId).stream().findFirst()
                .flatMap(agentRepository::findById).map(Agent::getType).map(FIELD_WORDS::get).orElse("");
        List<Map<String, Object>> posts = rank(blogService.publishedCards(), words(about.toString()), words(field)).stream()
                .limit(MAX_POSTS)
                .map(post -> {
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("slug", post.slug());
                    card.put("title", post.title());
                    card.put("summary", post.summary());
                    card.put("imagePath", post.imagePath());
                    return card;
                })
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lessons", lessons);
        out.put("posts", posts);
        return out;
    }

    /** Posts sharing words with the room first (a word of the companion's field counts less), then the newest. */
    static List<BlogService.PublicPost> rank(List<BlogService.PublicPost> posts, Set<String> about, Set<String> field) {
        Map<BlogService.PublicPost, Integer> score = new LinkedHashMap<>();
        for (BlogService.PublicPost post : posts) {
            Set<String> text = words(String.join(" ", nullToEmpty(post.title()), nullToEmpty(post.summary()), nullToEmpty(post.tags())));
            int points = 0;
            for (String word : text) {
                if (about.contains(word)) {
                    points += 3;
                } else if (field.contains(word)) {
                    points += 1;
                }
            }
            score.put(post, points);
        }
        return posts.stream()
                .sorted(Comparator.<BlogService.PublicPost>comparingInt(score::get).reversed()
                        .thenComparing(post -> post.publishedAt() == null ? 0L : post.publishedAt(), Comparator.reverseOrder()))
                .toList();
    }

    static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        for (String word : nullToEmpty(text).toLowerCase(Locale.ROOT).split("[^\\p{L}\\u200c]+")) {
            String clean = word.replace("‌", "");
            if (clean.length() >= MIN_WORD && !COMMON.contains(word)) {
                out.add(clean);
            }
        }
        return out;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
