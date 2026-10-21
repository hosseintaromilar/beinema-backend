package com.coupleai.coupleai.beinema.counseling;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRetrieverTest {

    private static final KnowledgeBase KB = new KnowledgeBase();
    private final KnowledgeRetriever retriever = new KnowledgeRetriever(KB);

    @Test
    void theKnowledgeBaseLoadsWithSourcesAndUniqueIds() {
        List<KbCard> cards = KB.all();
        assertTrue(cards.size() >= 70, "principles, rules, safety, contrasts, counter-examples and patterns: " + cards.size());
        Set<String> ids = new HashSet<>();
        for (KbCard card : cards) {
            assertTrue(ids.add(card.id()), "duplicate " + card.id());
            assertTrue(Set.of("THEORY", "PRACTICE", "BEINEMA_DESIGN").contains(card.sourceKind()), card.id());
            assertFalse(card.sourceRef().isBlank(), card.id() + " has no reference");
            assertFalse(card.tags().isEmpty(), card.id() + " has no tags");
        }
    }

    @Test
    void safetyCardsComeFirstWhenThereIsASafetyCue() {
        List<KbCard> cards = retriever.retrieve(Set.of("SAFETY_CUE", "FOCUS_W", "BLAME"), 8, 3200);
        assertEquals(KbCard.Type.SAFETY, cards.get(0).type());
        assertTrue(cards.stream().anyMatch(card -> card.id().equals("S-02")), "ambiguous fear");
    }

    @Test
    void noSafetyCardsWithoutASafetyTag() {
        List<KbCard> cards = retriever.retrieve(Set.of("FOCUS_W", "UNCONFIRMED_WANTS", "DOUBT_RELATIONSHIP"), 8, 3200);
        assertTrue(cards.stream().noneMatch(card -> card.type() == KbCard.Type.SAFETY));
        assertTrue(cards.stream().anyMatch(card -> card.id().equals("R-AMB-01")), "ambiguity that changes the route");
        assertTrue(cards.stream().anyMatch(card -> card.id().equals("C-01")), "tired of the fights versus doubting");
    }

    @Test
    void theSizeStaysWithinTheLimits() {
        Set<String> everything = Set.of("FOCUS_D", "FOCUS_E", "FOCUS_P", "BLAME", "ANGER", "NONCOMPLIANCE", "PLAN_PENDING",
                "CONSTRAINT", "QUIET_PARTNER", "MOTIVATION_LOW", "SESSION_END_NEAR");
        List<KbCard> cards = retriever.retrieve(everything, 8, 3200);
        assertTrue(cards.size() <= 8);
        int chars = cards.stream().mapToInt(card -> card.text().length() + 1).sum();
        assertTrue(chars <= 3200, "chars " + chars);
    }

    @Test
    void theSameSituationAlwaysGetsTheSameCards() {
        Set<String> tags = Set.of("FOCUS_P", "NONCOMPLIANCE", "PLAN_PENDING");
        assertEquals(retriever.retrieve(tags, 8, 3200), retriever.retrieve(tags, 8, 3200));
    }
}
