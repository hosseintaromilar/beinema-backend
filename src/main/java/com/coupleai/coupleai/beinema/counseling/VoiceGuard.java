package com.coupleai.coupleai.beinema.counseling;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** What the voice wrote is checked once: written Persian in spoken talk, and saying again what it just said. */
public final class VoiceGuard {

    private static final Pattern WRITTEN = Pattern.compile(
            "(^|[\\s،؛.؟!«])(را|است|هستم|هستند|می‌باشد|می‌باشند|بفرمایید)(?=$|[\\s،؛.؟!»])");
    private static final double SAME = 0.7;
    private static final int MIN_WORDS = 6;

    private VoiceGuard() {
    }

    /** The first written-Persian word in the reply, if any. */
    static Optional<String> written(String reply) {
        var matcher = WRITTEN.matcher(reply == null ? "" : reply);
        return matcher.find() ? Optional.of(matcher.group(2)) : Optional.empty();
    }

    /**
     * Words of advice in spoken Persian: a step, a suggestion, an instruction. Only in a sentence that is
     * not a question: «می‌تونی بگی از کی شروع شد؟» asks, «می‌تونی از فردا کوتاه خداحافظی کنی» advises.
     */
    private static final Pattern ADVICE = Pattern.compile(
            "(پیشنهاد می[‌ ]?کنم|پیشنهادم|توصیه می[‌ ]?کنم|توصیه‌م|بهتره|بهتر است|بهتر نیست|سعی کن|سعی کنید|امتحان کن|"
                    + "امتحان کنید|از فردا|از امشب|یه راهش|راهش اینه|کافیه که|قدم اول|تمرین کن|تمرین کنید|"
                    + "(?<![\\p{L}\u200c])می[\u200c ]?تونی(د)? |"
                    + "باید [^؟?]{0,30}(کنی|بگی|بذاری|کنید|بگید|بذارید))");

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!؟?\\n])\\s*");

    /** The first sentence of the reply that gives advice, if any; questions never count. */
    static Optional<String> advises(String reply) {
        for (String sentence : SENTENCE_END.split(reply == null ? "" : reply)) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty() || trimmed.endsWith("؟") || trimmed.endsWith("?")) {
                continue;
            }
            var matcher = ADVICE.matcher(trimmed + (trimmed.matches(".*[.!،]$") ? "" : "."));
            if (matcher.find()) {
                return Optional.of(trimmed);
            }
        }
        return Optional.empty();
    }

    /** The reply without its sentences of advice (questions and everything else are kept). */
    static String withoutAdvice(String reply) {
        StringBuilder out = new StringBuilder();
        for (String sentence : SENTENCE_END.split(reply == null ? "" : reply)) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean question = trimmed.endsWith("؟") || trimmed.endsWith("?");
            if (!question && ADVICE.matcher(trimmed + (trimmed.matches(".*[.!،]$") ? "" : ".")).find()) {
                continue;
            }
            out.append(out.length() == 0 ? "" : " ").append(trimmed);
        }
        return out.toString();
    }

    /** True when the reply says nearly what one of the companion's recent messages already said. */
    static boolean repeats(String reply, List<String> earlier) {
        Set<String> mine = new HashSet<>(EchoGuard.words(reply == null ? "" : reply));
        if (mine.size() < MIN_WORDS) {
            return false;
        }
        for (String text : earlier) {
            Set<String> other = new HashSet<>(EchoGuard.words(text == null ? "" : text));
            Set<String> shared = new HashSet<>(mine);
            shared.retainAll(other);
            Set<String> all = new HashSet<>(mine);
            all.addAll(other);
            if (!all.isEmpty() && (double) shared.size() / all.size() >= SAME) {
                return true;
            }
        }
        return false;
    }
}
