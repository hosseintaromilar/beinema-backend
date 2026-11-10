package com.coupleai.coupleai.beinema.guide;

import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Exception.QuestionAnswerException;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import com.coupleai.coupleai.beinema.metis.MetisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The general guide: a free chat that helps someone who does not know where to start find the
 * companion that fits. It never does a companion's work; it asks and routes.
 *
 * Nothing of the conversation is stored: the browser sends the earlier messages with each new one
 * and the guide reads them in a fresh Metis session that is forgotten at once. Only a count of
 * messages per day is kept in memory, so the free chat cannot be used without limit: a visitor gets
 * a few messages and then logs in to go on; a logged-in person has a larger daily allowance.
 * The companions it may suggest are read from the database on every message, so what the admin
 * panel shows is what the guide knows; a suggested id that is not an active companion is dropped.
 */
@Service
public class GuideService {

    static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    static final int MAX_MESSAGE = 1000;
    static final int MAX_HISTORY = 30;
    static final int MAX_HISTORY_CHARS = 1500;
    private static final Pattern AGENT = Pattern.compile("\\[\\[AGENT\\|ID=(\\d{1,9})\\]\\]");

    private final MetisService metisService;
    private final AgentRepository agentRepository;
    private final String botId;
    private final int guestMessages;
    private final int userMessages;

    /** key (ip:… or user:…) to the messages sent today. */
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private volatile LocalDate countsDay = LocalDate.now(TEHRAN);

    public GuideService(MetisService metisService, AgentRepository agentRepository,
                        @Value("${metis.guide-bot-id:}") String botId,
                        @Value("${beinema.guide.guest-messages:6}") int guestMessages,
                        @Value("${beinema.guide.user-messages:60}") int userMessages) {
        this.metisService = metisService;
        this.agentRepository = agentRepository;
        this.botId = botId;
        this.guestMessages = guestMessages;
        this.userMessages = userMessages;
    }

    public record Turn(String role, String text) {
    }

    public record Suggestion(Long id, String name, String headline, String photoUrl, String type) {
    }

    public record Answer(String reply, List<Suggestion> agents, Integer remaining) {
    }

    public Answer ask(List<Turn> history, String message, User user, String address) {
        String latest = message == null ? "" : message.strip();
        if (latest.isEmpty()) {
            throw new QuestionAnswerException("EMPTY", HttpStatus.BAD_REQUEST, "پیامت خالیه.");
        }
        if (latest.length() > MAX_MESSAGE) {
            throw new QuestionAnswerException("TOO_LONG", HttpStatus.BAD_REQUEST, "پیام کوتاه‌تری بنویس.");
        }
        if (botId == null || botId.isBlank()) {
            throw new QuestionAnswerException("GUIDE_OFF", HttpStatus.SERVICE_UNAVAILABLE, "راهنما فعلاً در دسترس نیست.");
        }
        int remaining = take(user, address);

        List<Agent> agents = agentRepository.findByStatus(AgentStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(Agent::getId)).toList();
        String packet = packet(agents, history, latest);
        MetisConversationResponse session = metisService.createConversation(botId,
                user == null ? "guide-guest" : "guide-" + user.getId(), user == null ? "مهمان" : user.visibleName());
        String raw;
        try {
            raw = metisService.sendMessage(session.getId(), packet);
        } finally {
            metisService.forget(session.getId());
        }
        return answer(raw, agents, user == null ? remaining : null);
    }

    /** Counts this message; throws when the day's allowance is used. Returns what is left after it. */
    int take(User user, String address) {
        LocalDate today = LocalDate.now(TEHRAN);
        if (!today.equals(countsDay)) {
            counts.clear();
            countsDay = today;
        }
        String key = user == null ? "ip:" + address : "user:" + user.getId();
        int limit = user == null ? guestMessages : userMessages;
        int used = counts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (used > limit) {
            counts.get(key).decrementAndGet();
            if (user == null) {
                throw new QuestionAnswerException("LOGIN_REQUIRED", HttpStatus.FORBIDDEN,
                        "برای ادامهٔ گفتگو با راهنما وارد حسابت شو؛ همین گفتگو همون‌جا ادامه پیدا می‌کنه و باز هم رایگانه.");
            }
            throw new QuestionAnswerException("DAILY_LIMIT", HttpStatus.TOO_MANY_REQUESTS,
                    "امروز به سقف گفتگو با راهنما رسیدی. می‌تونی از صفحهٔ همراه‌ها خودت یکی رو انتخاب کنی یا فردا ادامه بدی.");
        }
        return limit - used;
    }

    static String packet(List<Agent> agents, List<Turn> history, String latest) {
        StringBuilder out = new StringBuilder("[COMPANIONS]\n");
        for (Agent agent : agents) {
            out.append("id=").append(agent.getId()).append(" | ").append(clean(agent.getName(), 120))
                    .append(" | field: ").append(agent.getType())
                    .append(" | ").append(clean(agent.getHeadline(), 200))
                    .append(" | helps with: ").append(clean(agent.getSpecialties(), 300))
                    .append(" | ").append(clean(agent.getDescription(), 400)).append('\n');
        }
        out.append("[CONVERSATION]\n");
        List<Turn> earlier = history == null ? List.of() : history;
        if (earlier.isEmpty()) {
            out.append("none\n");
        }
        for (Turn turn : earlier.subList(Math.max(0, earlier.size() - MAX_HISTORY), earlier.size())) {
            String role = "GUIDE".equalsIgnoreCase(turn.role()) ? "GUIDE" : "USER";
            out.append(role).append(": ").append(clean(turn.text(), MAX_HISTORY_CHARS)).append('\n');
        }
        out.append("[LATEST]\n").append(clean(latest, MAX_MESSAGE)).append('\n');
        return out.toString();
    }

    /** The reply without its markers, and the cards of the active companions it named (at most two). */
    static Answer answer(String raw, List<Agent> agents, Integer remaining) {
        String text = raw == null ? "" : raw;
        Map<Long, Agent> active = new LinkedHashMap<>();
        agents.forEach(agent -> active.put(agent.getId(), agent));
        List<Suggestion> cards = new ArrayList<>();
        Matcher matcher = AGENT.matcher(text);
        while (matcher.find() && cards.size() < 2) {
            Agent agent = active.get(Long.valueOf(matcher.group(1)));
            if (agent != null && cards.stream().noneMatch(card -> card.id().equals(agent.getId()))) {
                cards.add(new Suggestion(agent.getId(), agent.getName(), agent.getHeadline(), agent.getPhotoUrl(),
                        agent.getType() == null ? null : agent.getType().name()));
            }
        }
        String reply = AGENT.matcher(text).replaceAll("").replaceAll("\\n{3,}", "\n\n").strip();
        if (reply.isEmpty() && cards.isEmpty()) {
            reply = "ببخشید، الان نتونستم جواب بدم. یه بار دیگه بنویس.";
        }
        return new Answer(reply, cards, remaining);
    }

    /** One line of text, cut to a length, without the markers that structure the packet. */
    static String clean(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.replace("[[", "[").replace("]]", "]").replaceAll("\\[(COMPANIONS|CONVERSATION|LATEST)\\]", "")
                .replaceAll("\\s+", " ").strip();
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
