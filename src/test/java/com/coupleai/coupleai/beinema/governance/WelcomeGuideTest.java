package com.coupleai.coupleai.beinema.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomeGuideTest {

    private static String owner(String others) {
        return WelcomeGuide.firstEntry(new WelcomeGuide.Facts(
                "افسانه", "زوج‌درمانگر", true, others, false, 45, 15, true, 15, 3));
    }

    private static String partner(boolean started) {
        return WelcomeGuide.firstEntry(new WelcomeGuide.Facts(
                "حسین", "زوج‌درمانگر", false, "افسانه", started, 45, 15, true, 15, 3));
    }

    @Test
    void theRoomCreatorGetsTheWholeWelcomeWithTheInviteGuide() {
        String text = owner("");

        assertTrue(text.startsWith("سلام افسانه، به بین‌ما خوش اومدی."));
        // 1. who the counselor is, under the Beinema brand
        assertTrue(text.contains("من زوج‌درمانگر هستم؛ همراه هوشمند زوج‌ها در بین‌ما."));
        // 2. how the work goes and which framework it follows
        assertTrue(text.contains("روال کار در بین‌ما:"));
        assertTrue(text.contains("واقعیت‌درمانی") && text.contains("نظریهٔ انتخاب"));
        assertTrue(text.contains("هر جلسه ۴۵ دقیقه است") && text.contains("۱۵ دقیقه استراحت"));
        // 3. how to invite the partner
        assertTrue(text.contains("منوی سه‌نقطه را در گوشهٔ بالا سمت چپ") && text.contains("«افزودن پارتنر»"));
        // 4. rules, safety, then the request to agree
        assertTrue(text.contains("قوانین گفتگو:") && text.contains("توهین"));
        assertTrue(text.contains("ایمنی:") && text.contains("۱۲۳") && text.contains("۱۱۵") && text.contains("۱۱۰"));
        assertTrue(text.endsWith("«موافقم» را بزن تا شروع کنیم."));
        assertFalse(text.contains("{"), "every placeholder is filled");
        // the order asked for: introduction, process and framework, invite, rules and safety, consent
        int intro = text.indexOf("من زوج‌درمانگر هستم؛ همراه هوشمند زوج‌ها در بین‌ما.");
        int process = text.indexOf("روال کار در بین‌ما:");
        int invite = text.indexOf("دعوت پارتنر:");
        int rules = text.indexOf("قوانین گفتگو:");
        int safety = text.indexOf("ایمنی:");
        int consent = text.indexOf("«موافقم»");
        assertTrue(intro < process && process < invite && invite < rules && rules < safety && safety < consent);
    }

    @Test
    void thePartnerGetsTheSameRulesWithoutTheInviteGuide() {
        String text = partner(true);

        assertTrue(text.startsWith("سلام حسین، به بین‌ما خوش اومدی."));
        assertTrue(text.contains("افسانه تو را به این گروه دعوت کرده"));
        assertTrue(text.contains("گفتگوی افسانه با من از قبل شروع شده"));
        assertTrue(text.contains("قوانین گفتگو:") && text.contains("ایمنی:") && text.contains("واقعیت‌درمانی"));
        assertFalse(text.contains("افزودن پارتنر"), "the partner does not need the invite guide");
        assertTrue(text.endsWith("«موافقم» را بزن تا وارد گفتگو شوی."));
        assertFalse(text.contains("{"));

        assertFalse(partner(false).contains("از قبل شروع شده"));
    }

    @Test
    void aCreatorWhosePartnerIsAlreadyInTheRoomIsNotSentToTheMenu() {
        String text = owner("حسین");
        assertFalse(text.contains("«افزودن پارتنر»"));
        assertTrue(text.contains("حسین هم عضو این گروه است"));
    }

    @Test
    void afterConsentTheCounselorThanksAndAsksWhatBroughtThem() {
        String first = WelcomeGuide.afterConsent("افسانه", false);
        // the rules are accepted before entering, so nobody is thanked for agreeing
        assertTrue(first.startsWith("افسانه، خوشحالم"));
        assertFalse(first.contains("موافقت کردی"));
        assertTrue(first.contains("خوشحالم که این مسیر را با هم شروع می‌کنیم"));
        assertTrue(first.contains("چه چیزی تو را به بین‌ما آورده"));
        assertTrue(first.contains("امیدواری این گفتگوها به چه تغییری برسد"));
        assertTrue(first.contains("چه نوع کمکی از من برایت مفیدتر است؟"));

        String joined = WelcomeGuide.afterConsent("حسین", true);
        assertTrue(joined.contains("خوشحالم که حالا هر دو نفرتان اینجا هستید"));
        assertTrue(joined.contains("نگاه تو هم به همان اندازه برایم مهم است"));
        assertFalse(joined.contains("{"));
    }

    @Test
    void theShortWelcomeHasNoRulesAndTheRulesPageHasThemAll() {
        String owner = WelcomeGuide.shortWelcome(new WelcomeGuide.Facts(
                "افسانه", "نادر", true, "", false, 45, 15, true, 15, 3));
        assertTrue(owner.startsWith("سلام افسانه، به بین‌ما خوش اومدی."));
        assertTrue(owner.contains("من نادر هستم"));
        assertTrue(owner.contains("افزودن پارتنر"), "the creator learns how to bring the partner in");
        assertFalse(owner.contains("قوانین گفتگو"));
        assertFalse(owner.contains("موافقم"));

        String partner = WelcomeGuide.shortWelcome(new WelcomeGuide.Facts(
                "حسین", "نادر", false, "افسانه", true, 45, 15, true, 15, 3));
        assertTrue(partner.contains("افسانه تو را به این گروه دعوت کرده"));
        assertTrue(partner.contains("پیام‌های قبلی را می‌بینی"));
        assertFalse(partner.contains("{"));

        String rules = WelcomeGuide.rules(45, 15, 15, 3, true);
        assertTrue(rules.contains("قوانین گفتگو"));
        assertTrue(rules.contains("ایمنی"));
        assertTrue(rules.contains("۴۵ دقیقه"));
        assertFalse(rules.contains("{"));
    }

    @Test
    void otherAgentsDoNotClaimRealityTherapy() {
        String text = WelcomeGuide.firstEntry(new WelcomeGuide.Facts(
                "افسانه", "مشاور مالی", true, "", false, 45, 15, false, 15, 3));
        assertFalse(text.contains("واقعیت‌درمانی"));
        assertTrue(text.contains("گفتگو ساختاریافته و بی‌طرف است"));
        assertTrue(text.contains("من مشاور مالی هستم؛ همراه هوشمند زوج‌ها در بین‌ما."));
    }

    @Test
    void aHumanNameReadsNaturally() {
        assertEquals("من نادر هستم؛ همراه هوشمند زوج‌ها در بین‌ما.", WelcomeGuide.introduction("نادر"));
        assertEquals("من نادر، مشاور زوج‌درمانگر بین‌ما هستم.",
                WelcomeGuide.introduction("نادر، مشاور زوج‌درمانگر بین‌ما"));
        assertEquals("من نادر، مشاور زوج‌درمانگر بین ما هستم.",
                WelcomeGuide.introduction(" نادر، مشاور زوج‌درمانگر بین ما. "));
        assertEquals("من نادر، زوج‌درمانگر در بین‌ما هستم.", WelcomeGuide.introduction("نادر، زوج‌درمانگر"));
        assertEquals("من مشاور بین‌ما هستم.", WelcomeGuide.introduction(""));

        String text = WelcomeGuide.firstEntry(new WelcomeGuide.Facts(
                "افسانه", "نادر، مشاور زوج‌درمانگر بین‌ما", true, "", false, 45, 15, true, 15, 3));
        assertTrue(text.contains("من نادر، مشاور زوج‌درمانگر بین‌ما هستم. اینجا فضایی خصوصی"));
        assertFalse(text.contains("بین‌ما بین‌ما"));
        assertFalse(text.contains("{"));
    }
}
