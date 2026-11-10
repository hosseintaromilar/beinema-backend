package com.coupleai.coupleai.beinema.safety;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.chat.ChatRealtimeService;
import com.coupleai.coupleai.beinema.counseling.PersianText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Safety that never waits for the model: every person's message is checked against the editable
 * phrase list before anything else, also while the AI service is down. A match shows the fixed
 * card with the help numbers, privately, once per session and kind of danger; to the writer when
 * they may be in danger, and to the other people of the room when the writer threatens someone.
 * Nothing shows in the shared chat, and only the kind of danger is logged, never the words.
 */
@Service
public class SafetyCardService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SafetyCardService.class);

    public static final String CARD = """
            اگر در خطر هستی یا کسی تهدیدت می‌کند، تنها نیستی:
            • ۱۱۰ — پلیس (خطر فوری)
            • ۱۱۵ — اورژانس
            • ۱۲۳ — اورژانس اجتماعی (خشونت خانگی، آزار کودک، بحران)
            • ۱۴۸۰ — صدای مشاور (مشاورهٔ تلفنی بهزیستی)
            • پلیس فتا (پلیس فضای مجازی) برای اخاذی و تهدید به پخش عکس: سایت cyberpolice.ir یا تماس با ۱۱۰

            اگر کسی با عکس یا فیلم تهدیدت می‌کند: عکس بیشتری نفرست، پول نده، از گفتگوها اسکرین‌شات بگیر و هیچ مدرکی را پاک نکن.""";

    private final SafetyRuleRepository ruleRepository;
    private final SafetyEventRepository eventRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRealtimeService chatRealtimeService;

    public SafetyCardService(SafetyRuleRepository ruleRepository, SafetyEventRepository eventRepository,
                             ChatRoomParticipantRepository participantRepository,
                             @org.springframework.context.annotation.Lazy ChatRealtimeService chatRealtimeService) {
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.chatRealtimeService = chatRealtimeService;
    }

    public record Match(String category, String audience) {
    }

    /** Checks one message of a person and shows the card where it is needed. Never throws. */
    public void check(ChatRoom room, User writer, String text) {
        if (room == null || writer == null || text == null || text.isBlank()) {
            return;
        }
        try {
            List<Match> matches = matches(text, ruleRepository.findAllByEnabledTrue());
            if (matches.isEmpty()) {
                return;
            }
            Integer session = room.getSessionNumber() == null ? 1 : room.getSessionNumber();
            for (Match match : matches) {
                for (Long recipient : recipients(room, writer, match.audience())) {
                    if (eventRepository.existsByChatRoomIdAndUserIdAndCategoryAndSessionNumber(
                            room.getId(), recipient, match.category(), session)) {
                        continue;
                    }
                    SafetyEvent event = new SafetyEvent();
                    event.setChatRoomId(room.getId());
                    event.setUserId(recipient);
                    event.setCategory(match.category());
                    event.setSessionNumber(session);
                    eventRepository.save(event);
                    chatRealtimeService.savePrivateSystemAndPublish(room.getId(), recipient, CARD);
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Safety check failed in room {}: {}", room.getId(), failure.getMessage());
        }
    }

    /** The kinds of danger a text matches, each with who should see the card. */
    public static List<Match> matches(String text, List<SafetyRule> rules) {
        String plain = compact(text);
        Map<String, Match> found = new LinkedHashMap<>();
        for (SafetyRule rule : rules) {
            String pattern = compact(rule.getPattern());
            if (!pattern.isEmpty() && plain.contains(pattern)) {
                found.putIfAbsent(rule.getCategory() + ":" + rule.getAudience(),
                        new Match(rule.getCategory(), rule.getAudience()));
            }
        }
        return new ArrayList<>(found.values());
    }

    /** ی/ک, half-spaces, Persian digits and spaces all compared the same way. */
    static String compact(String text) {
        StringBuilder digits = new StringBuilder();
        for (char c : PersianText.normalize(text).toCharArray()) {
            if (c >= '۰' && c <= '۹') {
                digits.append((char) ('0' + (c - '۰')));
            } else if (c >= '٠' && c <= '٩') {
                digits.append((char) ('0' + (c - '٠')));
            } else if (!Character.isWhitespace(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }

    private Set<Long> recipients(ChatRoom room, User writer, String audience) {
        Set<Long> people = new LinkedHashSet<>();
        if (SafetyRule.OTHERS.equals(audience)) {
            for (ChatRoomParticipant person : ChatRoomParticipant.active(participantRepository.findAllByChatRoom(room))) {
                if (person.getUser() != null && !person.getUser().getId().equals(writer.getId())) {
                    people.add(person.getUser().getId());
                }
            }
        } else {
            people.add(writer.getId());
        }
        return people;
    }

    /** The first phrase list, only into an empty table; after that the admin panel owns it. */
    @Override
    public void run(ApplicationArguments args) {
        if (ruleRepository.count() > 0) {
            return;
        }
        List<SafetyRule> rules = new ArrayList<>();
        add(rules, SafetyRule.SELF_HARM, SafetyRule.SELF, "fa", "خودکشی", "خودمو بکشم", "خودم رو بکشم", "خودم را بکشم",
                "میخوام بمیرم", "دلم میخواد بمیرم", "کاش بمیرم", "کاش مرده بودم", "به زندگیم پایان", "رگمو بزنم",
                "خودمو از بین ببرم", "دیگه نمیخوام زنده", "قرص زیاد بخورم");
        add(rules, SafetyRule.SELF_HARM, SafetyRule.SELF, "finglish", "khodkoshi", "mikham bemiram", "khodamo bekosham");
        add(rules, SafetyRule.VIOLENCE, SafetyRule.SELF, "fa", "کتکم", "کتک زد", "کتک میزنه", "منو زد", "زدتم",
                "منو میزنه", "سیلی زد", "هلم داد", "خفم کرد", "گلومو گرفت", "منو میکشه", "تهدید به مرگ", "تهدیدم میکنه",
                "چاقو کشید", "میترسم منو بکشه");
        add(rules, SafetyRule.VIOLENCE, SafetyRule.OTHERS, "fa", "میکشمت", "بکشمت", "حسابتو میرسم", "حسابت رو میرسم",
                "پشیمونت میکنم", "بلایی سرت میارم", "میزنمت", "دهنتو صاف میکنم");
        add(rules, SafetyRule.VIOLENCE, SafetyRule.OTHERS, "finglish", "mikoshamet", "bekoshamet");
        add(rules, SafetyRule.SEXTORTION, SafetyRule.SELF, "fa", "عکسامو پخش", "عکسهامو پخش", "فیلممو پخش",
                "تهدید میکنه عکس", "تهدید کرده عکس", "اخاذی", "باج میخواد", "آبرومو میبره", "عکس خصوصیمو");
        add(rules, SafetyRule.SEXTORTION, SafetyRule.OTHERS, "fa", "عکساتو پخش میکنم", "عکستو پخش میکنم",
                "فیلمتو پخش میکنم", "آبروتو میبرم", "میفرستم برای خانوادت");
        add(rules, SafetyRule.CHILD_ABUSE, SafetyRule.SELF, "fa", "بچه رو میزنه", "بچه رو کتک", "بچمو میزنه",
                "بچه هامو میزنه", "با کمربند میزنه", "با کمربند زد", "بچه رو زد", "دست درازی به بچه", "آزار جنسی بچه",
                "بچه رو آزار");
        ruleRepository.saveAll(rules);
        log.info("Safety phrase list seeded: {} rules", rules.size());
    }

    private static void add(List<SafetyRule> rules, String category, String audience, String lang, String... patterns) {
        for (String pattern : patterns) {
            SafetyRule rule = new SafetyRule();
            rule.setCategory(category);
            rule.setAudience(audience);
            rule.setLang(lang);
            rule.setPattern(pattern);
            rules.add(rule);
        }
    }
}
