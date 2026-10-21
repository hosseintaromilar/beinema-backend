package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The options of a structured question. The voice writes the options for the
 * readings; the platform always adds the same escape options, so a partner can
 * always reject our readings or say they are not sure yet.
 */
public final class QuestionOptions {

    public static final String HYPOTHESIS = "HYPOTHESIS";
    public static final String NONE = "NONE";
    public static final String UNSURE = "UNSURE";
    public static final String YES = "YES";
    public static final String NO = "NO";

    public static final String NONE_LABEL = "هیچ‌کدوم دقیقاً منظورم نیست";
    public static final String UNSURE_LABEL = "هنوز خودم هم مطمئن نیستم";
    public static final String YES_LABEL = "بله، دقیقاً";
    public static final String NO_LABEL = "نه، منظورم چیز دیگه‌ایه";
    public static final String CONFIRM_UNSURE_LABEL = "مطمئن نیستم";

    /** needsText: the partner must explain in their own words. */
    public record Option(String id, String label, String kind, Long intentId, boolean needsText) {
    }

    private QuestionOptions() {
    }

    public static List<Option> forChoose(Map<Long, String> labelsByIntent) {
        List<Option> options = new ArrayList<>();
        int index = 1;
        for (Map.Entry<Long, String> entry : labelsByIntent.entrySet()) {
            options.add(new Option("o" + index++, entry.getValue().trim(), HYPOTHESIS, entry.getKey(), false));
        }
        options.add(new Option("none", NONE_LABEL, NONE, null, true));
        options.add(new Option("unsure", UNSURE_LABEL, UNSURE, null, false));
        return options;
    }

    public static List<Option> forConfirm(Long intentId) {
        return List.of(
                new Option("yes", YES_LABEL, YES, intentId, false),
                new Option("no", NO_LABEL, NO, intentId, false),
                new Option("unsure", CONFIRM_UNSURE_LABEL, UNSURE, intentId, false)
        );
    }

    public static String toJson(List<Option> options) {
        return CounselingStateStore.json(options);
    }

    public static List<Option> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return CounselingStateStore.JSON.readValue(json, new TypeReference<List<Option>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    public static Option find(ClarifyingQuestion question, String optionId) {
        if (question == null || optionId == null) {
            return null;
        }
        return read(question.getOptionsJson()).stream()
                .filter(option -> option.id().equals(optionId))
                .findFirst()
                .orElse(null);
    }

    /** The kind of the chosen option of an answered question, or "". */
    public static String kindOf(ClarifyingQuestion question, String optionId) {
        Option option = find(question, optionId);
        return option == null ? "" : option.kind();
    }
}
