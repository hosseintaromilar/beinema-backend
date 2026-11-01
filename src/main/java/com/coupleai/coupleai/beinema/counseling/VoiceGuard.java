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
