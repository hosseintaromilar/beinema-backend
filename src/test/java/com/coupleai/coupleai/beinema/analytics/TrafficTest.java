package com.coupleai.coupleai.beinema.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficTest {

    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
    private static final String WINDOWS_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final String ANDROID_TABLET = "Mozilla/5.0 (Linux; Android 13; SM-X200) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    @Test
    void referringSitesAreNamed() {
        assertEquals("GOOGLE", Traffic.source("https://www.google.com/", null, null).key());
        assertEquals("GOOGLE", Traffic.source("https://www.google.co.ir/search?q=x", null, null).key());
        assertEquals("GEMINI", Traffic.source("https://gemini.google.com/app", null, null).key());
        assertEquals("INSTAGRAM", Traffic.source("https://l.instagram.com/?u=https%3A%2F%2Fbeinema.ir", null, null).key());
        assertEquals("TELEGRAM", Traffic.source("https://t.me/somechannel/12", null, null).key());
        assertEquals("CHATGPT", Traffic.source("https://chatgpt.com/", null, null).key());
        Traffic.Source other = Traffic.source("https://blog.example.org/post", null, null);
        assertEquals("OTHER", other.key());
        assertEquals("blog.example.org", other.host());
    }

    @Test
    void ourOwnPagesAndNothingAreDirect() {
        assertEquals("DIRECT", Traffic.source("https://beinema.ir/agents", null, null).key());
        assertEquals("DIRECT", Traffic.source("", null, null).key());
        assertEquals("DIRECT", Traffic.source(null, null, null).key());
    }

    @Test
    void withoutAReferrerTheCampaignTagNamesTheSource() {
        assertEquals("INSTAGRAM", Traffic.source(null, "ig", "social").key());
        assertEquals("TELEGRAM", Traffic.source("", "telegram", null).key());
        assertEquals("EMAIL", Traffic.source(null, "newsletter", "email").key());
        assertEquals("OTHER", Traffic.source(null, "test.com", null).key(), "t.co is not inside test.com");
    }

    @Test
    void devicesBrowsersAndSystems() {
        assertEquals("MOBILE", Traffic.device(IPHONE));
        assertEquals("iOS", Traffic.system(IPHONE));
        assertEquals("Safari", Traffic.browser(IPHONE));
        assertEquals("DESKTOP", Traffic.device(WINDOWS_CHROME));
        assertEquals("Chrome", Traffic.browser(WINDOWS_CHROME));
        assertEquals("Windows", Traffic.system(WINDOWS_CHROME));
        assertEquals("TABLET", Traffic.device(ANDROID_TABLET));
    }

    @Test
    void robotsAreNotCounted() {
        assertTrue(Traffic.robot("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        assertTrue(Traffic.robot("TelegramBot (like TwitterBot)"));
        assertTrue(Traffic.robot("curl/8.4.0"));
        assertTrue(Traffic.robot(null));
        assertFalse(Traffic.robot(IPHONE));
    }

    @Test
    void pathsGroupWell() {
        assertEquals("/chat/:id", Traffic.path("/chat/52?tab=1#x"));
        assertEquals("/", Traffic.path(""));
        assertEquals("/join/:token", Traffic.path("/join/a8F3kL9qZ2xW7mN4pR6tY1"));
        assertEquals("/blog/رابطه-سالم", Traffic.path("/blog/%D8%B1%D8%A7%D8%A8%D8%B7%D9%87-%D8%B3%D8%A7%D9%84%D9%85"));
        assertEquals("/agents", Traffic.path("/agents/"));
    }

    @Test
    void periodsAndTheOneBefore() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 15, 30); // 1405/08/18, a Monday
        AnalyticsService.Period today = AnalyticsService.period("today", now);
        assertEquals(LocalDateTime.of(2026, 11, 9, 0, 0), today.from());
        assertEquals(LocalDateTime.of(2026, 11, 8, 15, 30), today.previousTo());
        assertTrue(today.hourly());

        AnalyticsService.Period week = AnalyticsService.period("week", now);
        assertEquals(LocalDateTime.of(2026, 11, 7, 0, 0), week.from(), "the Persian week starts on Saturday");

        AnalyticsService.Period month = AnalyticsService.period("month", now);
        assertEquals(LocalDateTime.of(2026, 10, 23, 0, 0), month.from(), "1 Aban 1405");
        assertEquals(LocalDate.of(2026, 9, 23), month.previousFrom().toLocalDate(), "1 Mehr 1405");

        AnalyticsService.Period seven = AnalyticsService.period("7d", now);
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0), seven.from());
        assertNull(Traffic.clean("  ", 10));
    }
}
