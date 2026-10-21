package com.coupleai.coupleai.beinema.counseling;

import java.util.Set;

/**
 * One item of the professional knowledge base, already rendered as the short
 * text the director receives. sourceKind separates published theory (THEORY),
 * professional practice (PRACTICE) and Beinema's own design choices
 * (BEINEMA_DESIGN), so none of them passes for another.
 */
public record KbCard(
        String id,
        Type type,
        String title,
        Set<String> tags,
        String text,
        String sourceKind,
        String sourceRef,
        String summaryFa
) {

    public enum Type { SAFETY, RULE, CONTRAST, COUNTEREXAMPLE, PATTERN, PRINCIPLE }
}
