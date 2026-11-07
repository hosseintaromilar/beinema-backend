package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.FinanceCard;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Exception.QuestionAnswerException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.FinanceCardRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Service.SessionLifecycleService;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import com.coupleai.coupleai.beinema.market.MarketDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The cards of the financial companion's path: the risk quiz, the household's financial picture,
 * a market report and a plan of scenarios. People fill and choose; the code computes; the
 * director reads it all in [FINANCE]. A person's quiz answers reach the other only when both
 * have answered; the picture is the household's and both see it.
 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final FinanceCardRepository cards;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final SessionLifecycleService sessionLifecycleService;
    private final ParticipationPauseService participationPauseService;
    private final MarketDataService marketData;

    /** What a person did on a card: the message it put in the room, and whether everyone has answered now. */
    public record Done(Message message, boolean everyoneAnswered) {
    }

    // ------------------------------------------------------------------ opening cards (the director asks)

    /** The card to send in this turn, or empty with the reason in notes. */
    @Transactional
    public Optional<FinanceCard> open(Long roomId, String kind, List<String> notes) {
        List<FinanceCard> given = cards.findAllByChatRoomIdOrderByIdAsc(roomId);
        switch (kind) {
            case FinanceCard.RISK -> {
                if (given.stream().anyMatch(card -> FinanceCard.RISK.equals(card.getKind()))) {
                    notes.add("finance: the risk quiz was already given");
                    return Optional.empty();
                }
                return Optional.of(save(roomId, kind, Map.of("answers", Map.of())));
            }
            case FinanceCard.PICTURE -> {
                // one picture per household: the same card again, with what is in it now
                return Optional.of(cards.findFirstByChatRoomIdAndKindOrderByIdDesc(roomId, FinanceCard.PICTURE)
                        .orElseGet(() -> save(roomId, kind, Map.of("values", Map.of(), "hints", Map.of()))));
            }
            case FinanceCard.MARKET -> {
                MarketDataService.Snapshot now = marketData.snapshot();
                if (now.quotes().isEmpty() || now.stale(Instant.now())) {
                    notes.add("finance: no fresh market prices, no market card");
                    return Optional.empty();
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("quotes", now.quotes());
                data.put("updatedAt", now.updatedAt().toEpochMilli());
                return Optional.of(save(roomId, kind, data));
            }
            case FinanceCard.PLAN -> {
                List<FinanceKit.Risk> risks = new ArrayList<>(risks(roomId).values());
                FinanceKit.Plan plan = FinanceKit.plan(risks, pictureValues(roomId));
                if (plan == null) {
                    notes.add("finance: no plan before someone has answered the risk quiz");
                    return Optional.empty();
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("plan", plan);
                data.put("choices", Map.of());
                return Optional.of(save(roomId, kind, data));
            }
            default -> {
                notes.add("finance: unknown card " + kind);
                return Optional.empty();
            }
        }
    }

    /** Figures the director heard in the conversation: offered in the picture's form, saved only by a person. */
    @Transactional
    public void hint(Long roomId, Map<String, Double> hints) {
        if (hints == null || hints.isEmpty()) {
            return;
        }
        FinanceCard card = cards.findFirstByChatRoomIdAndKindOrderByIdDesc(roomId, FinanceCard.PICTURE)
                .orElseGet(() -> save(roomId, FinanceCard.PICTURE, Map.of("values", Map.of(), "hints", Map.of())));
        Map<String, Object> data = data(card);
        Map<String, Object> kept = new LinkedHashMap<>(map(data.get("hints")));
        hints.forEach((id, value) -> {
            if (field(id) != null && value != null && value >= 0 && value < 1_000_000_000d) {
                kept.put(id, value);
            }
        });
        data.put("hints", kept);
        card.setDataJson(CounselingStateStore.json(data));
        cards.save(card);
    }

    // ------------------------------------------------------------------ what people do

    @Transactional
    public Done answerRisk(Long roomId, String publicId, Map<String, Integer> answers, User user) {
        ChatRoom room = active(roomId, user);
        FinanceCard card = locked(roomId, publicId, FinanceCard.RISK);
        Map<String, Object> data = data(card);
        Map<String, Object> all = new TreeMap<>(map(data.get("answers")));
        if (all.containsKey(String.valueOf(user.getId()))) {
            return new Done(null, false);
        }
        if (!FinanceKit.complete(answers)) {
            throw new QuestionAnswerException("TEST_INCOMPLETE", HttpStatus.BAD_REQUEST, "لطفاً به همهٔ سؤال‌ها جواب بده.");
        }
        Map<String, Integer> kept = new LinkedHashMap<>();
        FinanceKit.RISK_ITEMS.forEach(item -> kept.put(item.id(), answers.get(item.id())));
        all.put(String.valueOf(user.getId()), kept);
        data.put("answers", all);
        card.setDataJson(CounselingStateStore.json(data));
        card.setUpdatedBy(user.getId());
        cards.save(card);
        boolean everyone = all.size() >= present(room);
        return new Done(say(room, user, "به «" + FinanceKit.RISK_TITLE + "» جواب دادم."), everyone);
    }

    @Transactional
    public Done savePicture(Long roomId, String publicId, Map<String, Object> body, User user) {
        ChatRoom room = active(roomId, user);
        FinanceCard card = locked(roomId, publicId, FinanceCard.PICTURE);
        Map<String, Object> data = data(card);
        boolean first = map(data.get("values")).isEmpty();
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> sent = map(body.get("values"));
        for (FinanceKit.Field field : FinanceKit.FIELDS) {
            Double value = number(sent.get(field.id()));
            if (value != null) {
                if (value < 0 || value >= 1_000_000_000d) {
                    throw new QuestionAnswerException("BAD_VALUE", HttpStatus.BAD_REQUEST, "عدد «" + field.label() + "» درست نیست.");
                }
                values.put(field.id(), value);
            }
        }
        if (values.isEmpty()) {
            throw new QuestionAnswerException("EMPTY", HttpStatus.BAD_REQUEST, "دست‌کم یکی از عددها رو بنویس.");
        }
        Object housing = body.get("housing");
        data.put("values", values);
        data.put("housing", housing != null && FinanceKit.HOUSING.containsKey(housing.toString()) ? housing.toString() : null);
        data.put("hints", Map.of());
        data.put("updatedAt", Instant.now().toEpochMilli());
        card.setDataJson(CounselingStateStore.json(data));
        card.setUpdatedBy(user.getId());
        cards.save(card);
        return new Done(say(room, user, first ? "تصویر مالی‌مون رو ثبت کردم." : "تصویر مالی‌مون رو به‌روز کردم."), true);
    }

    @Transactional
    public Done choose(Long roomId, String publicId, int index, User user) {
        ChatRoom room = active(roomId, user);
        FinanceCard card = locked(roomId, publicId, FinanceCard.PLAN);
        Map<String, Object> data = data(card);
        List<Object> scenarios = list(map(data.get("plan")).get("scenarios"));
        if (index < -1 || index >= scenarios.size()) {
            throw new QuestionAnswerException("BAD_CHOICE", HttpStatus.BAD_REQUEST, "این گزینه نیست.");
        }
        Map<String, Object> choices = new TreeMap<>(map(data.get("choices")));
        choices.put(String.valueOf(user.getId()), index);
        data.put("choices", choices);
        card.setDataJson(CounselingStateStore.json(data));
        card.setUpdatedBy(user.getId());
        cards.save(card);
        String text = index < 0
                ? "فعلاً هیچ‌کدوم از سناریوها رو انتخاب نمی‌کنم."
                : "سناریوی «" + map(scenarios.get(index)).get("title") + "» رو انتخاب کردم.";
        return new Done(say(room, user, text), true);
    }

    // ------------------------------------------------------------------ what people see

    @Transactional(readOnly = true)
    public Map<String, Object> view(Long roomId, String publicId, User user) {
        ChatRoom room = member(roomId, user);
        FinanceCard card = cards.findByPublicId(publicId)
                .filter(found -> found.getChatRoomId().equals(roomId))
                .orElseThrow(() -> new QuestionAnswerException("CARD_NOT_FOUND", HttpStatus.NOT_FOUND, "کارت پیدا نشد."));
        Map<String, Object> data = data(card);
        Map<Long, String> names = names(room);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", card.getPublicId());
        view.put("kind", card.getKind());
        switch (card.getKind()) {
            case FinanceCard.RISK -> riskView(view, room, data, names, user);
            case FinanceCard.PICTURE -> pictureView(view, data, names, card);
            case FinanceCard.MARKET -> {
                view.put("quotes", data.get("quotes"));
                view.put("updatedAt", data.get("updatedAt"));
                view.put("source", "tgju.org، tsetmc.com");
            }
            default -> {
                view.put("plan", data.get("plan"));
                Map<String, Object> choices = map(data.get("choices"));
                view.put("myChoice", choices.get(String.valueOf(user.getId())));
                List<Map<String, Object>> chosen = new ArrayList<>();
                choices.forEach((id, index) -> chosen.add(Map.of("name", names.getOrDefault(Long.valueOf(id), "یکی از شما"),
                        "me", id.equals(String.valueOf(user.getId())), "index", index)));
                view.put("choices", chosen);
            }
        }
        return view;
    }

    private void riskView(Map<String, Object> view, ChatRoom room, Map<String, Object> data, Map<Long, String> names, User user) {
        Map<String, Object> all = map(data.get("answers"));
        view.put("title", FinanceKit.RISK_TITLE);
        view.put("intro", FinanceKit.RISK_INTRO);
        view.put("minutes", 3);
        List<Map<String, Object>> steps = new ArrayList<>();
        FinanceKit.RISK_ITEMS.forEach(item -> steps.add(Map.of("id", item.id(), "prompt", item.prompt(),
                "options", item.options(), "label", item.label())));
        view.put("steps", steps);
        boolean done = all.containsKey(String.valueOf(user.getId()));
        view.put("done", done);
        boolean everyone = all.size() >= present(room);
        view.put("completed", everyone);
        view.put("waitingFor", names.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(user.getId()) && !all.containsKey(String.valueOf(entry.getKey())))
                .map(Map.Entry::getValue).findFirst().orElse(null));
        List<Map<String, Object>> people = new ArrayList<>();
        all.forEach((id, answers) -> {
            boolean me = id.equals(String.valueOf(user.getId()));
            if (me || everyone) {
                people.add(person(names.getOrDefault(Long.valueOf(id), "یکی از شما"), me, FinanceKit.risk(ints(answers))));
            }
        });
        view.put("people", people);
    }

    private static Map<String, Object> person(String name, boolean me, FinanceKit.Risk risk) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("name", name);
        person.put("me", me);
        person.put("profile", risk.profile());
        person.put("title", FinanceKit.profileTitle(risk.profile()));
        person.put("text", FinanceKit.profileText(risk.profile()));
        person.put("willingness", risk.willingness());
        person.put("capacity", risk.capacity());
        person.put("mismatch", risk.mismatch());
        return person;
    }

    private void pictureView(Map<String, Object> view, Map<String, Object> data, Map<Long, String> names, FinanceCard card) {
        Map<String, Double> values = doubles(data.get("values"));
        List<Map<String, Object>> fields = new ArrayList<>();
        FinanceKit.FIELDS.forEach(field -> fields.add(Map.of("id", field.id(), "label", field.label(), "group", field.group())));
        view.put("fields", fields);
        view.put("housingOptions", FinanceKit.HOUSING);
        view.put("values", values);
        view.put("housing", data.get("housing"));
        view.put("hints", data.get("hints"));
        view.put("updatedAt", data.get("updatedAt"));
        view.put("updatedBy", card.getUpdatedBy() == null ? null : names.get(card.getUpdatedBy()));
        view.put("summary", values.isEmpty() ? null : FinanceKit.picture(values));
    }

    // ------------------------------------------------------------------ the director's view

    /** The [FINANCE] block: what has been done on the path and what it says. */
    @Transactional(readOnly = true)
    public String forDirector(TurnMaterial m) {
        StringBuilder out = new StringBuilder("[FINANCE]\n");
        List<FinanceCard> given = cards.findAllByChatRoomIdOrderByIdAsc(m.roomId());
        Map<Long, FinanceKit.Risk> risks = risks(m.roomId());
        boolean quizGiven = given.stream().anyMatch(card -> FinanceCard.RISK.equals(card.getKind()));
        out.append("risk quiz: ");
        if (!quizGiven) {
            out.append("NOT_GIVEN\n");
        } else if (risks.isEmpty()) {
            out.append("GIVEN, nobody has answered yet\n");
        } else {
            out.append('\n');
            risks.forEach((userId, risk) -> out.append("  ").append(role(m, userId)).append(": ").append(risk.profile())
                    .append(" (willingness ").append(risk.willingness()).append(", capacity ").append(risk.capacity()).append(')')
                    .append(risk.mismatch() ? " SAYS_AND_ACTS_DIFFERENTLY" : "").append('\n'));
            if (m.userIdOf("MEMBER") != null && risks.size() < 2) {
                out.append("  the other has not answered yet; do not reveal or compare\n");
            }
        }
        Map<String, Double> values = pictureValues(m.roomId());
        out.append("financial picture (million toman): ");
        if (values.isEmpty()) {
            out.append("NOT_FILLED\n");
        } else {
            out.append('\n');
            FinanceKit.FIELDS.forEach(field -> {
                if (values.get(field.id()) != null) {
                    out.append("  ").append(field.id()).append('=').append(FinanceKit.amount(values.get(field.id()))).append('\n');
                }
            });
            FinanceKit.Picture p = FinanceKit.picture(values);
            out.append("  monthly surplus=").append(p.surplus() == null ? "unknown" : FinanceKit.amount(p.surplus()))
                    .append(" saving rate=").append(percent(p.savingRate()))
                    .append(" instalments share=").append(percent(p.instalmentShare()))
                    .append(" reserve months=").append(p.reserveMonths() == null ? "unknown" : String.format(Locale.ROOT, "%.1f", p.reserveMonths()))
                    .append(" net worth=").append(p.netWorth() == null ? "unknown" : FinanceKit.amount(p.netWorth())).append('\n');
        }
        Map<String, Object> hints = cards.findFirstByChatRoomIdAndKindOrderByIdDesc(m.roomId(), FinanceCard.PICTURE)
                .map(card -> map(data(card).get("hints"))).orElse(Map.of());
        if (!hints.isEmpty()) {
            out.append("  figures you heard, waiting in the form for them to save: ").append(hints.keySet()).append('\n');
        }
        out.append("market cards sent: ").append(given.stream().filter(card -> FinanceCard.MARKET.equals(card.getKind())).count()).append('\n');
        Optional<FinanceCard> plan = cards.findFirstByChatRoomIdAndKindOrderByIdDesc(m.roomId(), FinanceCard.PLAN);
        if (plan.isEmpty()) {
            out.append("plan: NOT_SENT\n");
        } else {
            Map<String, Object> data = data(plan.get());
            List<Object> scenarios = list(map(data.get("plan")).get("scenarios"));
            out.append("plan: SENT, scenarios ");
            for (int i = 0; i < scenarios.size(); i++) {
                out.append(i).append('=').append(map(scenarios.get(i)).get("level")).append(' ');
            }
            out.append('\n');
            map(data.get("choices")).forEach((userId, index) -> out.append("  ").append(role(m, Long.valueOf(userId)))
                    .append(" chose ").append(Integer.parseInt(index.toString()) < 0 ? "none for now" : "scenario " + index).append('\n'));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ helpers

    private Map<Long, FinanceKit.Risk> risks(Long roomId) {
        Map<Long, FinanceKit.Risk> out = new LinkedHashMap<>();
        cards.findFirstByChatRoomIdAndKindOrderByIdDesc(roomId, FinanceCard.RISK).ifPresent(card ->
                map(data(card).get("answers")).forEach((id, answers) -> out.put(Long.valueOf(id), FinanceKit.risk(ints(answers)))));
        return out;
    }

    private Map<String, Double> pictureValues(Long roomId) {
        return cards.findFirstByChatRoomIdAndKindOrderByIdDesc(roomId, FinanceCard.PICTURE)
                .map(card -> doubles(data(card).get("values"))).orElse(Map.of());
    }

    private static String role(TurnMaterial m, Long userId) {
        return userId.equals(m.userIdOf("OWNER")) ? "OWNER" : userId.equals(m.userIdOf("MEMBER")) ? "MEMBER" : "SOMEONE";
    }

    private static String percent(Double value) {
        return value == null ? "unknown" : Math.round(value * 100) + "%";
    }

    private FinanceCard save(Long roomId, String kind, Map<String, Object> data) {
        FinanceCard card = new FinanceCard();
        card.setPublicId(UUID.randomUUID().toString());
        card.setChatRoomId(roomId);
        card.setKind(kind);
        card.setDataJson(CounselingStateStore.json(data));
        return cards.save(card);
    }

    private FinanceCard locked(Long roomId, String publicId, String kind) {
        return cards.findByPublicIdForUpdate(publicId)
                .filter(card -> card.getChatRoomId().equals(roomId) && kind.equals(card.getKind()))
                .orElseThrow(() -> new QuestionAnswerException("CARD_NOT_FOUND", HttpStatus.NOT_FOUND, "کارت پیدا نشد."));
    }

    private Message say(ChatRoom room, User user, String text) {
        int next = messageRepository.findMaxSequenceNumberByChatRoom(room).orElse(0) + 1;
        Message message = Message.builder()
                .chatRoom(room)
                .senderType(MessageSenderType.USER)
                .senderId(user.getId())
                .content(text)
                .status(MessageStatus.SENT)
                .sequenceNumber(next)
                .build();
        return messageRepository.saveAndFlush(message);
    }

    private ChatRoom active(Long roomId, User user) {
        ChatRoom room = member(roomId, user);
        try {
            sessionLifecycleService.requireOpen(room, user);
        } catch (com.coupleai.coupleai.beinema.Exception.SessionOnBreakException closed) {
            throw new QuestionAnswerException(closed.getCode(), HttpStatus.CONFLICT, closed.getMessage());
        }
        sessionLifecycleService.requireActive(room);
        participationPauseService.requireCanSend(room, user);
        return room;
    }

    private ChatRoom member(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new QuestionAnswerException("CARD_NOT_FOUND", HttpStatus.NOT_FOUND, "کارت پیدا نشد."));
        if (!participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new QuestionAnswerException("NOT_MEMBER", HttpStatus.FORBIDDEN, "دسترسی به این گفتگو نداری.");
        }
        return room;
    }

    /** How many people are in the room now (at least one): everyone of them answers the quiz. */
    private int present(ChatRoom room) {
        return (int) Math.max(1, participantRepository.findAllByChatRoom(room).stream()
                .filter(person -> person.getLeftAt() == null).count());
    }

    private Map<Long, String> names(ChatRoom room) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (ChatRoomParticipant participant : participantRepository.findAllByChatRoom(room)) {
            names.put(participant.getUser().getId(), participant.getUser().visibleName());
        }
        return names;
    }

    private static FinanceKit.Field field(String id) {
        return FinanceKit.FIELDS.stream().filter(field -> field.id().equals(id)).findFirst().orElse(null);
    }

    static Map<String, Object> data(FinanceCard card) {
        try {
            String json = card.getDataJson();
            return json == null || json.isBlank() ? new LinkedHashMap<>()
                    : CounselingStateStore.JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Unreadable finance card " + card.getPublicId(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private static Map<String, Integer> ints(Object value) {
        Map<String, Integer> out = new LinkedHashMap<>();
        map(value).forEach((key, item) -> {
            if (item instanceof Number number) {
                out.put(key, number.intValue());
            }
        });
        return out;
    }

    static Map<String, Double> doubles(Object value) {
        Map<String, Double> out = new LinkedHashMap<>();
        map(value).forEach((key, item) -> {
            Double number = number(item);
            if (number != null) {
                out.put(key, number);
            }
        });
        return out;
    }

    /** A number from the form: Persian or Latin digits, with or without separators; empty is no value. */
    static Double number(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        StringBuilder digits = new StringBuilder();
        for (char c : value.toString().toCharArray()) {
            if (c >= '۰' && c <= '۹') {
                digits.append((char) ('0' + (c - '۰')));
            } else if (c >= '٠' && c <= '٩') {
                digits.append((char) ('0' + (c - '٠')));
            } else if ((c >= '0' && c <= '9') || c == '.') {
                digits.append(c);
            } else if (c == '٫' || c == '/') {
                digits.append('.');
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(digits.toString());
        } catch (NumberFormatException wrong) {
            throw new QuestionAnswerException("BAD_VALUE", HttpStatus.BAD_REQUEST, "یکی از عددها درست نیست.");
        }
    }
}
