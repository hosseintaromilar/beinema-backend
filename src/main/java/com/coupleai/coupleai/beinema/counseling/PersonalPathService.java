package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.PersonMilestone;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Exception.ChatRoomAccessDeniedException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.PersonMilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A person's own path in the room, the four stages of WDEP as milestones: which
 * are reached, in which session, how many sessions each took, and the short line
 * the counselor wrote for them. Only that person sees it.
 */
@Service
@RequiredArgsConstructor
public class PersonalPathService {

    private static final Map<String, String[]> TITLES = Map.of(
            "W", new String[]{"روشن کردن خواسته‌ام", "خواسته‌ام روشن شد"},
            "D", new String[]{"دیدن کاری که الان می‌کنم", "کاری که الان می‌کنم را دیدم"},
            "E", new String[]{"ارزیابی خودم", "خودم ارزیابی کردم"},
            "P", new String[]{"انتخاب یک قدم کوچک", "قدم کوچکم را انتخاب کردم"});

    private final PersonMilestoneRepository milestoneRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;

    public Map<String, Object> forUser(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        if (user == null || !participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new ChatRoomAccessDeniedException("Access denied");
        }
        Map<String, PersonMilestone> reached = new LinkedHashMap<>();
        for (PersonMilestone milestone : milestoneRepository.findAllByChatRoomIdAndUserIdOrderByIdAsc(roomId, user.getId())) {
            reached.putIfAbsent(milestone.getStage(), milestone);
        }
        List<Map<String, Object>> stages = new ArrayList<>();
        String current = null;
        int previousSession = 1;
        for (String stage : DecisionValidator.STAGES) {
            PersonMilestone milestone = reached.get(stage);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("stage", stage);
            view.put("reached", milestone != null);
            view.put("title", TITLES.get(stage)[milestone != null ? 1 : 0]);
            if (milestone != null) {
                view.put("sessionNumber", milestone.getSessionNumber());
                view.put("sessions", Math.max(1, milestone.getSessionNumber() - previousSession + 1));
                view.put("note", milestone.getNote());
                previousSession = milestone.getSessionNumber();
            } else if (current == null) {
                current = stage;
            }
            stages.add(view);
        }
        Map<String, Object> path = new LinkedHashMap<>();
        path.put("stages", stages);
        path.put("current", current);
        path.put("sessionNumber", room.getSessionNumber() == null ? 1 : room.getSessionNumber());
        return path;
    }
}
