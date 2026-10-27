package com.coupleai.coupleai.beinema.counseling;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Catches a reply that hands a partner's words back to them («حسین پرسید چرا
 * همیشه…»، «وقتی می‌گی شب‌ها آروم باشه…»). The prompts forbid it, but the
 * voice still does it now and then; then it is asked once more to write the
 * reply without the echo. Only the spoken part is checked: the options of a
 * question card may use the partner's own words.
 */
final class EchoGuard {

    /** Five words in a row from what they wrote: an echo whatever the wording around it. */
    static final int LONG_RUN = 5;
    /** Three words in a row, in a sentence that says they said or asked it. */
    static final int SHORT_RUN = 3;
    private static final Pattern SPEECH_CUE = Pattern.compile(
            "(پرسید|پرسیدی|پرسیدین|گفتی|گفتین|گفت که|نوشتی|نوشت که|می‌گی|میگی|می‌گید|میگید|می‌گویی|می‌گوید|اینکه گفتی|حرفت)");

    private EchoGuard() {
    }

    /** The repeated words, when the reply echoes one of the new messages. */
    static Optional<String> echoed(String reply, List<String> partnerTexts) {
        if (reply == null || reply.isBlank() || partnerTexts == null || partnerTexts.isEmpty()) {
            return Optional.empty();
        }
        String spoken = reply.replaceAll("\\[\\[[^\\]]*\\]\\]", " ");
        for (String sentence : spoken.split("[.!؟?؛\\n]+")) {
            List<String> words = words(sentence);
            boolean cue = SPEECH_CUE.matcher(sentence).find();
            for (String text : partnerTexts) {
                List<String> theirs = words(text);
                Optional<String> run = sharedRun(words, theirs, cue ? SHORT_RUN : LONG_RUN);
                if (run.isPresent()) {
                    return run;
                }
            }
        }
        return Optional.empty();
    }

    /** The first run of at least min words that both texts contain in the same order. */
    static Optional<String> sharedRun(List<String> a, List<String> b, int min) {
        if (a.size() < min || b.size() < min) {
            return Optional.empty();
        }
        for (int i = 0; i + min <= a.size(); i++) {
            for (int j = 0; j + min <= b.size(); j++) {
                int length = 0;
                while (i + length < a.size() && j + length < b.size() && a.get(i + length).equals(b.get(j + length))) {
                    length++;
                }
                if (length >= min) {
                    return Optional.of(String.join(" ", a.subList(i, i + length)));
                }
            }
        }
        return Optional.empty();
    }

    /** Words for comparing: Arabic letters as Persian, no half-space, no punctuation. */
    static List<String> words(String text) {
        String normal = text == null ? "" : text
                .replace('ي', 'ی').replace('ك', 'ک')
                .replace("‌", "")
                .replaceAll("[\\p{Punct}«»،؛؟…\"“”'‌]+", " ")
                .toLowerCase();
        List<String> out = new ArrayList<>();
        for (String word : normal.trim().split("\\s+")) {
            if (!word.isBlank()) {
                out.add(word);
            }
        }
        return out;
    }
}
