package com.coupleai.coupleai.beinema.counseling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A plain Persian word list that looks for possible safety signals in new
 * messages. It decides nothing: a cue only makes the director look at the
 * message with the safety rules in front of it, and a HIGH cue that the
 * director ignored triggers one safety review. Missing a cue proves nothing.
 */
public final class SafetyScreen {

    public enum Severity { MEDIUM, HIGH }

    public record Cue(String category, Severity severity, String phrase, Long messageId, String role) {
    }

    public record Input(Long messageId, String role, String text) {
    }

    public record Result(List<Cue> cues) {
        public boolean any() {
            return !cues.isEmpty();
        }

        public boolean high() {
            return cues.stream().anyMatch(cue -> cue.severity() == Severity.HIGH);
        }

        public static Result none() {
            return new Result(List.of());
        }
    }

    private record Family(String category, Severity severity, String[] phrases) {
    }

    private static final List<Family> FAMILIES = List.of(
            family("SELF_HARM", Severity.HIGH,
                    "خودکشی", "خودمو بکشم", "خودم رو بکشم", "خودمرو بکشم", "خودم را بکشم", "میخوام بمیرم",
                    "دلم میخواد بمیرم", "کاش بمیرم", "کاش مرده بودم", "زنده نباشم", "به زندگیم پایان",
                    "رگمو بزنم", "رگم رو بزنم", "خودمو از بین ببرم", "دیگه نمیخوام زنده"),
            family("VIOLENCE", Severity.HIGH,
                    "کتکم", "کتک زد", "کتک میزنه", "کتکمون", "منو زد", "زدتم", "منو میزنه", "سیلی زد",
                    "سیلی میزنه", "هلم داد", "پرتم کرد", "خفم کرد", "خفه ام کرد", "خفه‌ام کرد", "گلومو گرفت",
                    "گلوم رو گرفت", "چاقو", "اسلحه", "تفنگ", "میکشمت", "بکشمت", "منو میکشه", "میکشتم",
                    "تهدید به مرگ"),
            family("THREAT", Severity.MEDIUM,
                    "تهدیدم", "تهدید کرد", "تهدید میکنه", "حسابتو میرسم", "حسابت رو میرسم", "پشیمونت میکنم",
                    "بلایی سرت", "بلایی سرم", "بلا سرم"),
            family("FEAR", Severity.MEDIUM,
                    "ازش میترسم", "از اون میترسم", "میترسم بگم", "میترسم چیزی بگم", "جرئت ندارم", "جرات ندارم",
                    "وحشت دارم", "ازش ترس دارم", "میترسم عصبانی بشه", "میترسم ازش", "ترسیدم ازش"),
            family("CONTROL", Severity.MEDIUM,
                    "گوشیمو چک", "گوشیم رو چک", "گوشیمو میگرده", "رمز گوشیم", "نمیذاره برم", "نمیذاره بیرون",
                    "اجازه نمیده برم", "اجازه نمیده با", "پولمو میگیره", "حقوقمو میگیره", "کارت بانکیمو",
                    "زندانی", "حبسم", "کنترلم میکنه", "تعقیبم میکنه", "جاسوسی", "لوکیشنمو"),
            family("CANT_SPEAK", Severity.MEDIUM,
                    "اینجا نمیتونم بگم", "جلوش نمیتونم", "نمیتونم راحت بگم", "اگه بگم بعدا", "بعدا تلافی",
                    "تلافی میکنه", "بعدش دعوام میکنه", "نمیتونم آزاد حرف")
    );

    private SafetyScreen() {
    }

    private static Family family(String category, Severity severity, String... phrases) {
        String[] normalized = new String[phrases.length];
        for (int i = 0; i < phrases.length; i++) {
            normalized[i] = PersianText.normalize(phrases[i]).replace(" ", "");
        }
        return new Family(category, severity, normalized);
    }

    public static Result screen(List<Input> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Result.none();
        }
        Map<String, Cue> found = new LinkedHashMap<>();
        for (Input input : inputs) {
            // spaces removed: «می ترسم», «میترسم» and «می‌ترسم» all match
            String text = PersianText.normalize(input.text()).replace(" ", "");
            if (text.isEmpty()) {
                continue;
            }
            for (Family family : FAMILIES) {
                for (String phrase : family.phrases()) {
                    if (!phrase.isEmpty() && text.contains(phrase)) {
                        found.putIfAbsent(
                                family.category() + ":" + input.messageId(),
                                new Cue(family.category(), family.severity(), phrase, input.messageId(), input.role())
                        );
                        break;
                    }
                }
            }
        }
        return new Result(new ArrayList<>(found.values()));
    }
}
