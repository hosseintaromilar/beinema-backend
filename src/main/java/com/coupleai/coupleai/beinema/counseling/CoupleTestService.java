package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.CoupleTest;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Exception.QuestionAnswerException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.CoupleTestRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import com.coupleai.coupleai.beinema.Service.SessionLifecycleService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tests a couple answers separately. A person's answers reach no one until both have
 * finished; the answer about fear never reaches anyone but that person.
 */
@Service
@RequiredArgsConstructor
public class CoupleTestService {

    private static final String FEAR = "FEAR";
    public static final String FEAR_NOTICE = "این جوابت توی نتیجه نمیاد و پارتنرت نمی‌بیندش. "
            + "اگه گاهی از واکنش کسی می‌ترسی، مهمه که تنهاش نذاری: صدای مشاور ۱۴۸۰ و اورژانس اجتماعی ۱۲۳ کنارت هستن.";

    private final CoupleTestRepository testRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final SessionLifecycleService sessionLifecycleService;
    private final ParticipationPauseService participationPauseService;

    /** repeated: this person had already submitted. fearNotice: they get a private note. */
    public record Submitted(CoupleTest test, Message message, boolean finishedNow, boolean repeated, boolean fearNotice) {
    }

    /** A new test in the room; empty when this test was already given here. */
    @Transactional
    public java.util.Optional<CoupleTest> open(Long roomId, String key) {
        boolean given = testRepository.findAllByChatRoomIdOrderByIdAsc(roomId).stream()
                .anyMatch(test -> test.getTestKey().equals(key));
        if (given) {
            return java.util.Optional.empty();
        }
        CoupleTest test = new CoupleTest();
        test.setPublicId(UUID.randomUUID().toString());
        test.setChatRoomId(roomId);
        test.setTestKey(key);
        test.setAnswersJson("{}");
        return java.util.Optional.of(testRepository.save(test));
    }

    @Transactional
    public Submitted submit(Long roomId, String publicId, Map<String, Integer> answers, User user) {
        ChatRoom room = member(roomId, user);
        try {
            sessionLifecycleService.requireOpen(room, user);
        } catch (com.coupleai.coupleai.beinema.Exception.SessionOnBreakException closed) {
            throw new QuestionAnswerException(closed.getCode(), HttpStatus.CONFLICT, closed.getMessage());
        }
        CoupleTest test = testRepository.findByPublicIdForUpdate(publicId)
                .filter(found -> found.getChatRoomId().equals(roomId))
                .orElseThrow(() -> new QuestionAnswerException("TEST_NOT_FOUND", HttpStatus.NOT_FOUND, "آزمون پیدا نشد."));
        ElikaKit.Test definition = definition(test);
        Map<Long, Map<String, Integer>> all = answers(test);
        if (all.containsKey(user.getId())) {
            return new Submitted(test, null, false, true, false);
        }
        if (!ElikaKit.complete(definition, answers)) {
            throw new QuestionAnswerException("TEST_INCOMPLETE", HttpStatus.BAD_REQUEST, "لطفاً به همهٔ سؤال‌ها جواب بده.");
        }
        sessionLifecycleService.requireActive(room);
        participationPauseService.requireCanSend(room, user);

        Map<String, Integer> kept = new LinkedHashMap<>();
        definition.items().forEach(item -> {
            kept.put(item.id(), answers.get(item.id()));
            if (definition.scale()) {
                kept.put(item.id() + ElikaKit.FLEX_SUFFIX, answers.get(item.id() + ElikaKit.FLEX_SUFFIX));
            }
        });
        all.put(user.getId(), kept);
        test.setAnswersJson(CounselingStateStore.json(all));
        boolean finished = all.size() >= 2;
        test.setCompleted(finished);
        testRepository.save(test);

        // finishing is part of the conversation: the companion goes on from it
        int next = messageRepository.findMaxSequenceNumberByChatRoom(room).orElse(0) + 1;
        Message message = Message.builder()
                .chatRoom(room)
                .senderType(MessageSenderType.USER)
                .senderId(user.getId())
                .content("آزمون «" + definition.title() + "» رو جواب دادم.")
                .status(MessageStatus.SENT)
                .sequenceNumber(next)
                .build();
        messageRepository.saveAndFlush(message);
        Integer fear = kept.get(FEAR);
        return new Submitted(test, message, finished, false, fear != null && fear >= 1);
    }

    /** The card as this person may see it. */
    @Transactional(readOnly = true)
    public Map<String, Object> view(Long roomId, String publicId, User user) {
        ChatRoom room = member(roomId, user);
        CoupleTest test = testRepository.findByPublicId(publicId)
                .filter(found -> found.getChatRoomId().equals(roomId))
                .orElseThrow(() -> new QuestionAnswerException("TEST_NOT_FOUND", HttpStatus.NOT_FOUND, "آزمون پیدا نشد."));
        ElikaKit.Test definition = definition(test);
        Map<Long, Map<String, Integer>> all = answers(test);
        Map<Long, String> names = names(room);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", test.getPublicId());
        view.put("key", definition.key());
        view.put("kind", definition.kind());
        view.put("title", definition.title());
        view.put("intro", definition.intro());
        view.put("minutes", definition.minutes());
        view.put("done", all.containsKey(user.getId()));
        view.put("doneCount", all.size());
        view.put("completed", test.isCompleted());
        view.put("waitingFor", waitingFor(names, all, user.getId()));
        List<Map<String, Object>> steps = new ArrayList<>();
        for (ElikaKit.Item item : definition.items()) {
            steps.add(step(item.id(), item.prompt(), item.options(), item.label()));
            if (definition.scale()) {
                steps.add(step(item.id() + ElikaKit.FLEX_SUFFIX, ElikaKit.FIRMNESS_PROMPT, ElikaKit.FIRMNESS, item.label()));
            }
        }
        view.put("steps", steps);
        if (test.isCompleted()) {
            List<Long> people = new ArrayList<>(all.keySet());
            ElikaKit.Comparison comparison = ElikaKit.compare(definition, all.get(people.get(0)), all.get(people.get(1)));
            view.put("people", List.of(person(people.get(0), names, user), person(people.get(1), names, user)));
            view.put("result", comparison);
        }
        return view;
    }

    /** The [TESTS] block of the director's packet, only for rooms with the kit. */
    @Transactional(readOnly = true)
    public String forDirector(TurnMaterial m) {
        Map<String, CoupleTest> given = new LinkedHashMap<>();
        testRepository.findAllByChatRoomIdOrderByIdAsc(m.roomId()).forEach(test -> given.put(test.getTestKey(), test));
        boolean alone = m.userIdOf("MEMBER") == null;
        StringBuilder out = new StringBuilder("[TESTS]\n");
        for (ElikaKit.Test test : ElikaKit.tests()) {
            out.append(test.key()).append(" | ").append(test.title()).append(" | ");
            CoupleTest row = given.get(test.key());
            if (row == null) {
                out.append("NOT_GIVEN | when: ").append(test.when()).append('\n');
                continue;
            }
            Map<Long, Map<String, Integer>> all = answers(row);
            if (row.isCompleted()) {
                out.append("COMPLETE\n");
                appendComparison(out, m, test, all);
            } else if (all.isEmpty()) {
                out.append("WAITING for both\n");
            } else if (alone) {
                out.append("ANSWERED by OWNER; MEMBER has not joined the room\n");
                appendOwn(out, test, all.values().iterator().next());
            } else {
                // what one answered stays closed until the other has answered too
                Long done = all.keySet().iterator().next();
                out.append("ANSWERED by ").append(done.equals(m.userIdOf("OWNER")) ? "OWNER" : "MEMBER")
                        .append("; the other has not. The answers stay closed.\n");
            }
        }
        return out.toString();
    }

    private static void appendComparison(StringBuilder out, TurnMaterial m, ElikaKit.Test test, Map<Long, Map<String, Integer>> all) {
        Map<String, Integer> owner = all.get(m.userIdOf("OWNER"));
        Map<String, Integer> member = all.get(m.userIdOf("MEMBER"));
        if (owner == null || member == null) {
            return;
        }
        ElikaKit.Comparison result = ElikaKit.compare(test, owner, member);
        out.append("  result: ").append(result.level()).append(" «").append(result.title()).append("»\n");
        for (ElikaKit.Row row : result.rows()) {
            out.append("  ").append(row.id()).append(' ').append(row.status())
                    .append(" OWNER=«").append(row.options().get(row.first())).append('»').append(firmness(row.firstFirmness()))
                    .append(" MEMBER=«").append(row.options().get(row.second())).append('»').append(firmness(row.secondFirmness()));
            if (row.seenDifferently()) {
                out.append(" SEEN_DIFFERENTLY");
            }
            out.append('\n');
        }
    }

    private static void appendOwn(StringBuilder out, ElikaKit.Test test, Map<String, Integer> own) {
        for (ElikaKit.Item item : test.items()) {
            if (item.hidden()) {
                continue;
            }
            out.append("  ").append(item.id()).append(" OWNER=«").append(item.options().get(own.get(item.id()))).append('»')
                    .append(firmness(own.get(item.id() + ElikaKit.FLEX_SUFFIX))).append('\n');
        }
    }

    private static String firmness(Integer value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case 0 -> " (RED_LINE)";
            case 1 -> " (preference)";
            default -> " (does not mind)";
        };
    }

    private static Map<String, Object> step(String id, String prompt, List<String> options, String label) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("prompt", prompt);
        step.put("options", options);
        step.put("label", label);
        return step;
    }

    private static Map<String, Object> person(Long id, Map<Long, String> names, User viewer) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("name", names.getOrDefault(id, "یکی از شما"));
        person.put("me", id.equals(viewer.getId()));
        return person;
    }

    private static String waitingFor(Map<Long, String> names, Map<Long, Map<String, Integer>> all, Long me) {
        return names.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(me) && !all.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private Map<Long, String> names(ChatRoom room) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (ChatRoomParticipant participant : participantRepository.findAllByChatRoom(room)) {
            names.put(participant.getUser().getId(), participant.getUser().visibleName());
        }
        return names;
    }

    private ChatRoom member(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new QuestionAnswerException("TEST_NOT_FOUND", HttpStatus.NOT_FOUND, "آزمون پیدا نشد."));
        if (!participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new QuestionAnswerException("NOT_MEMBER", HttpStatus.FORBIDDEN, "دسترسی به این گفتگو نداری.");
        }
        return room;
    }

    private static ElikaKit.Test definition(CoupleTest test) {
        return ElikaKit.test(test.getTestKey())
                .orElseThrow(() -> new QuestionAnswerException("TEST_NOT_FOUND", HttpStatus.NOT_FOUND, "آزمون پیدا نشد."));
    }

    /** Sorted by user id, so a person keeps their place and colour on every result. */
    static Map<Long, Map<String, Integer>> answers(CoupleTest test) {
        try {
            String json = test.getAnswersJson();
            if (json == null || json.isBlank()) {
                return new TreeMap<>();
            }
            return new TreeMap<>(CounselingStateStore.JSON.readValue(json, new TypeReference<Map<Long, Map<String, Integer>>>() {
            }));
        } catch (Exception exception) {
            throw new IllegalStateException("Unreadable answers of test " + test.getPublicId(), exception);
        }
    }
}
