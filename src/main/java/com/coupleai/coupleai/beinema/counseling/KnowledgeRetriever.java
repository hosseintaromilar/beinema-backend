package com.coupleai.coupleai.beinema.counseling;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the few knowledge cards that fit the situation of a turn, by the tags
 * the platform derived from memory and the new messages. Deterministic: the
 * same situation always gets the same cards. Safety cards always come first
 * when there is any safety tag.
 */
@Component
public class KnowledgeRetriever {

    public static final Set<String> SAFETY_TAGS = Set.of("SAFETY_CUE", "SAFETY_CUE_HIGH", "SAFETY_ACTIVE");

    /** How many cards of each type, at most, before the free fill. */
    private static final Map<KbCard.Type, Integer> QUOTA = new EnumMap<>(Map.of(
            KbCard.Type.SAFETY, 3,
            KbCard.Type.RULE, 4,
            KbCard.Type.CONTRAST, 1,
            KbCard.Type.COUNTEREXAMPLE, 1,
            KbCard.Type.PATTERN, 1,
            KbCard.Type.PRINCIPLE, 1
    ));

    private final KnowledgeBase knowledgeBase;

    public KnowledgeRetriever(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public List<KbCard> retrieve(Set<String> situation, int maxCards, int maxChars) {
        boolean safety = situation.stream().anyMatch(SAFETY_TAGS::contains);
        List<Scored> scored = new ArrayList<>();
        for (KbCard card : knowledgeBase.all()) {
            double score = score(card, situation);
            if (card.type() == KbCard.Type.SAFETY && !safety) {
                continue;
            }
            if (score > 0) {
                scored.add(new Scored(card, score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(item -> item.card().id()));

        Set<KbCard> chosen = new LinkedHashSet<>();
        int[] chars = {0};
        // 1. per type, in order of importance
        for (KbCard.Type type : KbCard.Type.values()) {
            int taken = 0;
            for (Scored item : scored) {
                if (item.card().type() != type || taken >= QUOTA.get(type) || chosen.size() >= maxCards) {
                    continue;
                }
                // patterns and principles only when they really match
                if ((type == KbCard.Type.PATTERN || type == KbCard.Type.PRINCIPLE) && item.score() < 3) {
                    continue;
                }
                if (add(chosen, item.card(), chars, maxChars)) {
                    taken++;
                }
            }
        }
        // 2. free fill by score
        for (Scored item : scored) {
            if (chosen.size() >= maxCards) {
                break;
            }
            if (!chosen.contains(item.card()) && item.score() >= 3) {
                add(chosen, item.card(), chars, maxChars);
            }
        }
        return new ArrayList<>(chosen);
    }

    static double score(KbCard card, Set<String> situation) {
        double score = 0;
        for (String tag : card.tags()) {
            if ("ALL".equals(tag)) {
                score += 0.5;
            } else if (situation.contains(tag)) {
                score += SAFETY_TAGS.contains(tag) ? 10 : tag.startsWith("FOCUS_") ? 1.5 : 3;
            }
        }
        return score;
    }

    private static boolean add(Set<KbCard> chosen, KbCard card, int[] chars, int maxChars) {
        int length = card.text().length() + 1;
        if (!chosen.isEmpty() && chars[0] + length > maxChars) {
            return false;
        }
        chosen.add(card);
        chars[0] += length;
        return true;
    }

    private record Scored(KbCard card, double score) {
    }
}
