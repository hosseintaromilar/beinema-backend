package com.coupleai.coupleai.beinema.governance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The counselor's first message to each person on their first entry to a room:
 * who the counselor is (Beinema), how the work goes and which framework it
 * follows, how to invite the partner (room creator only), the rules of the
 * conversation and safety, and finally the request to agree. Fixed text from
 * classpath:onboarding/first-entry-welcome.txt, so it is complete every time.
 */
public final class WelcomeGuide {

    static final String FILE = "onboarding/first-entry-welcome.txt";

    private static volatile Map<String, String> sections;

    /** Everything the welcome needs to know about the room and the person. */
    public record Facts(
            String name,
            String agentName,
            boolean roomCreator,
            String otherMembers,
            boolean conversationStarted,
            int sessionMinutes,
            int breakMinutes,
            boolean relationshipCounselor,
            int overtimeMinutes,
            int maxSessions
    ) {
    }

    private WelcomeGuide() {
    }

    public static String firstEntry(Facts facts) {
        Map<String, String> s = sections();
        String others = blank(facts.otherMembers()) ? "" : facts.otherMembers().trim();
        String process = s.get("process")
                .replace("{framework}", s.get(facts.relationshipCounselor() ? "framework_relationship" : "framework_general"))
                .replace("{minutes}", persianDigits(facts.sessionMinutes()))
                .replace("{break_minutes}", persianDigits(facts.breakMinutes()))
                .replace("{overtime_minutes}", persianDigits(facts.overtimeMinutes()))
                .replace("{max_sessions}", persianDigits(facts.maxSessions()));

        String text;
        if (facts.roomCreator()) {
            String partner = others.isEmpty() ? s.get("owner_invite") : s.get("owner_partner_here");
            text = s.get("owner").replace("{partner}", partner);
        } else {
            String started = facts.conversationStarted() ? s.get("partner_started") : "";
            text = s.get("partner").replace("{started}", started);
        }
        return text
                .replace("{process}", process)
                .replace("{rules}", s.get("rules"))
                .replace("{name}", blank(facts.name()) ? "" : facts.name().trim())
                .replace("{intro}", introduction(facts.agentName()))
                .replace("{inviter}", others.isEmpty() ? "پارتنرت" : others)
                .trim();
    }

    /**
     * The short welcome of someone who accepted the rules before entering (on
     * the create-room or invitation page): greeting, who the counselor is, and
     * for the room creator how to bring the partner in. No rules, no consent card.
     */
    public static String shortWelcome(Facts facts) {
        Map<String, String> s = sections();
        String others = blank(facts.otherMembers()) ? "" : facts.otherMembers().trim();
        String text;
        if (facts.roomCreator()) {
            String partner = others.isEmpty() ? s.get("owner_invite") : s.get("welcome_partner_here");
            text = s.get("welcome_owner").replace("{partner}", partner);
        } else {
            String started = facts.conversationStarted() ? s.get("welcome_started") : "";
            text = s.get("welcome_partner").replace("{started}", started);
        }
        return text
                .replace("{name}", blank(facts.name()) ? "" : facts.name().trim())
                .replace("{intro}", introduction(facts.agentName()))
                .replace("{inviter}", others.isEmpty() ? "پارتنرت" : others)
                .trim();
    }

    /**
     * How the work goes and the rules, for the page where a person accepts them
     * before creating or joining a room.
     */
    public static String rules(int sessionMinutes, int breakMinutes, int overtimeMinutes, int maxSessions,
                               boolean relationshipCounselor) {
        Map<String, String> s = sections();
        String process = s.get("process")
                .replace("{framework}", s.get(relationshipCounselor ? "framework_relationship" : "framework_general"))
                .replace("{minutes}", persianDigits(sessionMinutes))
                .replace("{break_minutes}", persianDigits(breakMinutes))
                .replace("{overtime_minutes}", persianDigits(overtimeMinutes))
                .replace("{max_sessions}", persianDigits(maxSessions));
        return (process + "\n\n" + s.get("rules")).trim();
    }

    /**
     * The counselor's first sentence, from the agent's name as the admin wrote
     * it. A plain name or role gets the brand after it; a name that already says
     * who the agent is (with a comma, or the brand) is used as it is.
     * «نادر» -> «من نادر هستم؛ همراه هوشمند زوج‌ها در بین‌ما.»
     * «نادر، مشاور زوج‌درمانگر بین‌ما» -> «من نادر، مشاور زوج‌درمانگر بین‌ما هستم.»
     * «نادر، زوج‌درمانگر» -> «من نادر، زوج‌درمانگر در بین‌ما هستم.»
     */
    static String introduction(String agentName) {
        String name = blank(agentName) ? "مشاور بین‌ما" : agentName.trim().replaceAll("[.،,؛;:]+$", "");
        boolean branded = name.replace("\u200c", "").replace(" ", "").contains("بینما");
        boolean described = name.contains("،") || name.contains(",");
        if (branded) {
            return "من " + name + " هستم.";
        }
        if (described) {
            return "من " + name + " در بین‌ما هستم.";
        }
        return "من " + name + " هستم؛ همراه هوشمند زوج‌ها در بین‌ما.";
    }

    /**
     * Right after a person agrees: thanks, gladness, and one open intake question
     * (what brought them, what they hope for, what help they want). partnerAlreadyIn:
     * the other partner had agreed before, so the person joins a conversation of two.
     */
    public static String afterConsent(String name, boolean partnerAlreadyIn) {
        Map<String, String> s = sections();
        return s.get(partnerAlreadyIn ? "consented_joined" : "consented_first")
                .replace("{name}", blank(name) ? "" : name.trim())
                .trim();
    }

    private static Map<String, String> sections() {
        Map<String, String> loaded = sections;
        if (loaded == null) {
            synchronized (WelcomeGuide.class) {
                if (sections == null) {
                    sections = read();
                }
                loaded = sections;
            }
        }
        return loaded;
    }

    static Map<String, String> read() {
        String raw;
        try (InputStream stream = WelcomeGuide.class.getClassLoader().getResourceAsStream(FILE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + FILE);
            }
            raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r", "");
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable " + FILE, exception);
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        String current = null;
        StringBuilder body = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            if (line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[[") && line.trim().endsWith("]]")) {
                if (current != null) {
                    parsed.put(current, trimBlankLines(body.toString()));
                }
                current = line.trim().substring(2, line.trim().length() - 2);
                body.setLength(0);
                continue;
            }
            if (current != null) {
                body.append(line).append('\n');
            }
        }
        if (current != null) {
            parsed.put(current, trimBlankLines(body.toString()));
        }
        for (String required : new String[]{"welcome_owner", "welcome_partner", "welcome_partner_here", "welcome_started",
                "owner", "owner_invite", "owner_partner_here", "partner", "partner_started",
                "process", "rules", "framework_relationship", "framework_general", "consented_first", "consented_joined"}) {
            if (!parsed.containsKey(required)) {
                throw new IllegalStateException(FILE + " has no [[" + required + "]] section");
            }
        }
        return parsed;
    }

    /** Leading and trailing empty lines go; a leading space (used to join a sentence) stays. */
    private static String trimBlankLines(String text) {
        String value = text;
        while (value.startsWith("\n")) {
            value = value.substring(1);
        }
        while (value.endsWith("\n")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String persianDigits(int number) {
        StringBuilder out = new StringBuilder();
        for (char c : String.valueOf(number).toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return out.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
