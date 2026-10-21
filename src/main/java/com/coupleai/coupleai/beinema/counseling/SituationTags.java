package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Enum.IntentKind;
import com.coupleai.coupleai.beinema.Enum.IntentStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives the situation tags of a turn from memory and from plain cues in the
 * new messages. The tags only choose which knowledge cards the director sees;
 * they never decide anything themselves.
 */
public final class SituationTags {

    /** Everything the tags are computed from. */
    public record Input(
            String speakerRole,
            List<String> freshTexts,
            List<String> freshRoles,
            List<String> recentRoles,
            CounselingSnapshot snapshot,
            List<ClientIntent> intents,
            boolean openQuestion,
            List<String> answerKinds,
            SafetyScreen.Result screen,
            List<TurnInbox.Note> notes,
            int sessionNumber,
            long minutesUsed,
            int minutesPlanned,
            long counselorTurnsThisSession
    ) {
    }

    private static final String[] BLAME = words("همیشه", "هیچوقت", "هیچ وقت", "تقصیر تو", "تقصیر اون", "تو باعث",
            "اون باعث", "مقصر", "بخاطر تو", "به خاطر تو", "اصلا براش مهم نیست", "اصلا برات مهم نیست");
    private static final String[] SELF_BLAME = words("تقصیر منه", "تقصیر خودمه", "من بدم", "من خرابش کردم",
            "همه چیو خراب", "به درد نمیخورم", "آدم بدی ام", "لیاقت ندارم", "من مقصرم");
    private static final String[] HOPELESS = words("فایده نداره", "فایده ای نداره", "دیگه نمیتونم", "خسته شدم",
            "خستم", "خسته ام", "بریدم", "امیدی ندارم", "ناامید", "دیگه نمیکشم");
    private static final String[] DOUBT = words("طلاق", "جدا بشیم", "جدایی", "ادامه بدیم یا نه", "تمومش کنیم",
            "این شکلی زندگی کنم", "رابطه رو تموم", "دیگه نمیخوام با", "نمیدونم میخوامش");
    private static final String[] ANGER = words("احمق", "خفه شو", "بیشعور", "عوضی", "دروغگو", "کثافت", "لعنتی",
            "آشغال", "گمشو", "بی عرضه", "نفهم");
    private static final String[] TO_COUNSELOR = words("مشاور", "به نظرت", "به نظر شما", "چیکار کنم", "چه کار کنم",
            "شما بگید", "تو بگو");
    private static final String[] EXPECTATION = words("بهش بگو", "شما بگید کی", "کی مقصره", "حق با کیه",
            "درستش کن", "عوضش کن", "قضاوت کن", "شما قضاوت");
    private static final String[] RELUCTANT = words("مشاوره فایده", "به زور اومدم", "مجبورم کرد", "نمیخواستم بیام",
            "به درد نمیخوره", "وقت تلفه", "مسخرس", "قبول ندارم مشاوره");
    private static final String[] NOT_DONE = words("نشد", "انجام ندادم", "نکردم", "یادم رفت", "وقت نشد", "نتونستم");
    private static final String[] CONSTRAINT = words("اجاره", "قسط", "پول", "حقوق", "شیفت", "سر کار", "بچه", "مریض",
            "مهدکودک", "خرج", "بدهی", "وام");
    private static final String[] PAST = words("قبلا", "اون موقع", "سال پیش", "سالها", "یادته", "اوایل");
    private static final String[] WANT_CHANGE = words("دیگه برام مهم نیست", "نظرم عوض", "الان بیشتر", "مهمتر اینه",
            "دیگه اونو نمیخوام", "منظورم این نبود");

    private SituationTags() {
    }

    private static String[] words(String... values) {
        String[] normalized = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = PersianText.normalize(values[i]);
        }
        return normalized;
    }

    public static Set<String> compute(Input in) {
        Set<String> tags = new LinkedHashSet<>();
        CounselingSnapshot snapshot = in.snapshot();

        // focus of the speaker and of the other partner
        if (in.speakerRole() != null) {
            tags.add("FOCUS_" + snapshot.focusOf(in.speakerRole()));
        }
        snapshot.focus.values().forEach(letter -> tags.add("FOCUS_" + letter));
        if (snapshot.focus.isEmpty()) {
            tags.add("FOCUS_NONE");
        }

        // session timing
        if (in.counselorTurnsThisSession() <= 2 || in.minutesUsed() < 6) {
            tags.add("SESSION_START");
        }
        if (in.minutesPlanned() > 0 && in.minutesUsed() >= in.minutesPlanned() - 7) {
            tags.add("SESSION_END_NEAR");
        }

        // questions and answers
        if (in.openQuestion()) {
            tags.add("OPEN_QUESTION");
        }
        for (String kind : in.answerKinds()) {
            tags.add("ANSWER");
            if ("NONE".equals(kind) || "NO".equals(kind)) {
                tags.add("ANSWER_NONE");
            }
            if ("UNSURE".equals(kind)) {
                tags.add("ANSWER_UNSURE");
            }
        }

        // wants
        boolean unconfirmedHigh = in.intents().stream().anyMatch(intent -> intent.getStatus() == IntentStatus.UNCONFIRMED
                && !"LOW".equals(intent.getImportance()));
        if (unconfirmedHigh) {
            tags.add("UNCONFIRMED_WANTS");
        }
        if (in.speakerRole() != null && in.intents().stream().noneMatch(intent -> intent.getKind() == IntentKind.WANT
                && in.speakerRole().equals(intent.getParticipantRole())
                && (intent.getStatus() == IntentStatus.CONFIRMED || intent.getStatus() == IntentStatus.UNCONFIRMED))) {
            tags.add("NO_WANTS_YET");
        }

        // plans
        boolean agreed = snapshot.plans.stream().anyMatch(plan -> "AGREED".equals(plan.status));
        if (agreed) {
            tags.add("PLAN_AGREED");
        }
        boolean pending = snapshot.plans.stream().anyMatch(plan -> "AGREED".equals(plan.status)
                && plan.session != null && plan.session < in.sessionNumber());
        if (pending) {
            tags.add("PLAN_PENDING");
        }

        // safety
        if (in.screen().any()) {
            tags.add("SAFETY_CUE");
        }
        if (in.screen().high()) {
            tags.add("SAFETY_CUE_HIGH");
        }
        int level = DecisionValidator.rank(snapshot.safety.level);
        if (level >= 2) {
            tags.add("SAFETY_ACTIVE");
        } else if (level == 1) {
            tags.add("SAFETY_CUE");
        }

        // what the director noticed last time
        for (String kind : snapshot.lastAnalysisKinds) {
            switch (kind) {
                case "WANT_CHANGE" -> tags.add("WANT_CHANGE");
                case "CONTRADICTION" -> tags.add("CONTRADICTION");
                case "RESISTANCE" -> tags.add("MOTIVATION_LOW");
                default -> {
                }
            }
        }

        // plain cues in the new messages
        String text = PersianText.normalize(String.join(" \n ", in.freshTexts()));
        int length = String.join("", in.freshTexts()).length();
        if (PersianText.containsAny(text, BLAME)) {
            tags.add("BLAME");
        }
        if (PersianText.containsAny(text, SELF_BLAME)) {
            tags.add("SELF_BLAME");
        }
        if (PersianText.containsAny(text, HOPELESS)) {
            tags.add("HOPELESS");
        }
        if (PersianText.containsAny(text, DOUBT)) {
            tags.add("DOUBT_RELATIONSHIP");
        }
        if (PersianText.containsAny(text, ANGER) || text.contains("!!")) {
            tags.add("ANGER");
        }
        if ((text.contains("؟") || text.contains("?")) && PersianText.containsAny(text, TO_COUNSELOR)) {
            tags.add("QUESTION_TO_COUNSELOR");
        }
        if (PersianText.containsAny(text, EXPECTATION)) {
            tags.add("UNREALISTIC_EXPECTATION");
        }
        if (PersianText.containsAny(text, RELUCTANT)) {
            tags.add("MOTIVATION_LOW");
        }
        if (agreed && PersianText.containsAny(text, NOT_DONE)) {
            tags.add("NONCOMPLIANCE");
        }
        if (PersianText.containsAny(text, CONSTRAINT)) {
            tags.add("CONSTRAINT");
        }
        if (PersianText.containsAny(text, PAST)) {
            tags.add("PAST_FOCUS");
        }
        if (PersianText.containsAny(text, WANT_CHANGE)) {
            tags.add("WANT_CHANGE");
        }
        if (length > 350) {
            tags.add("LONG_STORY");
            if (text.contains("چون") && PersianText.containsAny(text, words("دلیل", "تحلیل", "ریشه", "به خاطر اینکه"))) {
                tags.add("ANALYTICAL");
            }
        }
        if (!in.freshTexts().isEmpty() && in.freshTexts().stream().allMatch(item -> PersianText.words(item) <= 4)) {
            tags.add("SHORT_REPLY");
        }

        // platform notes
        for (TurnInbox.Note note : in.notes()) {
            if (note.text().contains("PAUSE_EXPIRED") || note.text().contains("REENTRY_COMMITTED")) {
                tags.add("PAUSE_RETURN");
            }
            if (note.text().contains("[ATTACHMENT")) {
                tags.add("ATTACHMENT");
            }
        }

        // who has been talking
        List<String> lastRoles = in.recentRoles();
        int from = Math.max(0, lastRoles.size() - 8);
        long owner = lastRoles.subList(from, lastRoles.size()).stream().filter(PartnerRole.OWNER::equals).count();
        long member = lastRoles.subList(from, lastRoles.size()).stream().filter(PartnerRole.MEMBER::equals).count();
        if ((owner == 0 && member >= 3) || (member == 0 && owner >= 3)) {
            tags.add("QUIET_PARTNER");
        }
        boolean bothNew = in.freshRoles().contains(PartnerRole.OWNER) && in.freshRoles().contains(PartnerRole.MEMBER);
        String previous = lastRoles.isEmpty() ? null : lastRoles.get(lastRoles.size() - 1);
        boolean answeredPartner = previous != null && PartnerRole.isPartner(previous) && in.speakerRole() != null
                && !previous.equals(in.speakerRole());
        if ((bothNew || answeredPartner) && !tags.contains("ANGER")) {
            tags.add("PARTNERS_TALKING");
        }
        return tags;
    }
}
