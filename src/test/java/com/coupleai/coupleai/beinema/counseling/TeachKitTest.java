package com.coupleai.coupleai.beinema.counseling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeachKitTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> OFFICIAL = Set.of("www.nhs.uk", "medlineplus.gov", "www.cdc.gov",
            "www.healthychildren.org", "www.stopbullying.gov");

    @Test
    void everyLessonIsAboutABeliefAndItsPracticeComesFromOfficialSources() {
        List<TeachKit.TeachLesson> lessons = TeachKit.all();
        assertTrue(lessons.size() >= 8);
        for (TeachKit.TeachLesson lesson : lessons) {
            assertTrue(lesson.id().startsWith(TeachKit.PREFIX), lesson.id());
            assertFalse(lesson.belief().isBlank(), lesson.id() + " belief");
            assertFalse(lesson.leadsTo().isBlank(), lesson.id() + " leads_to");
            assertTrue(lesson.whyNot().size() >= 2, lesson.id() + " why_not");
            assertFalse(lesson.truerBelief().isBlank(), lesson.id() + " truer belief");
            assertTrue(lesson.check().answer() >= 0 && lesson.check().answer() < lesson.check().options().size());
            assertFalse(lesson.practice().steps().isEmpty(), lesson.id() + " practice steps");
            for (TeachKit.Source source : lesson.practice().sources()) {
                String host = java.net.URI.create(source.url()).getHost();
                assertTrue(OFFICIAL.contains(host), lesson.id() + " source " + source.url());
            }
            lesson.prerequisites().forEach(first -> assertTrue(TeachKit.lesson(first).isPresent(), lesson.id() + " needs " + first));
        }
        assertTrue(Lessons.find("L-RAHA-LEAVING-IS-ABANDONING").isPresent(), "the common lesson lookup finds them too");
    }

    private static DecisionValidator.Context ctx() {
        return new DecisionValidator.Context(Set.of("OWNER"), "OWNER", Map.of(), Map.of(501L, "OWNER", 502L, "COUNSELOR"),
                Map.of(31L, new DecisionValidator.IntentView(31L, "OWNER", "CONFIRMED")), Map.of(), Set.of(), 0, 3,
                false, "NONE", Map.of(), true);
    }

    private static JsonNode decision(String lesson) throws Exception {
        return JSON.readTree("{\"schema\":\"director.v1\",\"analysis\":{\"speaker\":\"OWNER\"},"
                + "\"decision\":{\"action\":\"TEACH\",\"phase\":\"ADVISE\",\"addressee\":\"OWNER\"},"
                + "\"lesson\":" + lesson + ",\"brief\":{\"do\":\"teach it\",\"length\":\"SHORT\"}}");
    }

    @Test
    void noLessonBeforeTheirOwnWayWasFoundNotToWork() throws Exception {
        DirectorDecision noWay = DecisionValidator.validate(decision("{\"id\":\"L-RAHA-LEAVING-IS-ABANDONING\",\"why\":\"crying at nursery\"}"), ctx());
        assertEquals("EXPLORE_DOING", noWay.action, "without what they do now, find it out first");
        assertNull(noWay.lessonId);

        DirectorDecision fits = DecisionValidator.validate(decision("{\"id\":\"L-RAHA-LEAVING-IS-ABANDONING\","
                + "\"their_way\":\"یک جملهٔ کوتاه می‌گم و می‌رم\",\"evidence\":501,\"verdict\":\"FITS\"}"), ctx());
        assertEquals("EXPLORE_DOING", fits.action, "a way that works is looked at deeper, not corrected");
        assertEquals(DecisionValidator.LOOK_DEEPER, fits.briefDo);

        DirectorDecision companionWords = DecisionValidator.validate(decision("{\"id\":\"L-RAHA-LEAVING-IS-ABANDONING\","
                + "\"their_way\":\"x\",\"evidence\":502,\"verdict\":\"MISFIT\"}"), ctx());
        assertEquals("EXPLORE_DOING", companionWords.action, "the evidence must be their own message");
    }

    @Test
    void aLessonOnceTheirWayDoesNotWork() throws Exception {
        DirectorDecision d = DecisionValidator.validate(decision("{\"id\":\"L-RAHA-LEAVING-IS-ABANDONING\",\"why\":\"crying at nursery\","
                + "\"their_way\":\"هر روز ده دقیقه دم در می‌مونم تا آروم شه\",\"evidence\":501,\"verdict\":\"MISFIT\",\"readiness\":\"ACCEPT\"}"), ctx());
        assertEquals("TEACH", d.action);
        assertEquals("L-RAHA-LEAVING-IS-ABANDONING", d.lessonId);
        assertEquals("OWNER", d.addressee);
        assertEquals("ACCEPT", d.lessonReadiness);
        assertEquals(501L, d.lessonEvidence);
    }

    @Test
    void theCardKeepsThePracticeFromTheSourcesWhateverTheTeacherWrites() throws Exception {
        TeachKit.TeachLesson lesson = TeachKit.lesson("L-RAHA-LEAVING-IS-ABANDONING").orElseThrow();
        DirectorDecision d = new DirectorDecision();
        d.lessonReadiness = "ACCEPT";
        d.lessonTheirWay = "هر روز ده دقیقه دم در می‌مونم";
        String good = "{\"schema\":\"teach-card.v2\",\"title\":\"رفتن یعنی رها کردن نیست\","
                + "\"situation\":\"تو هر صبح دم در مهد می‌مونی تا آروم شه.\",\"belief\":\"اگه برم و گریه کنه، یعنی ولش کردم.\","
                + "\"leads_to\":\"می‌مونی.\",\"result\":\"گریه طولانی‌تر می‌شه.\",\"why_not\":[\"یک\",\"دو\"],"
                + "\"truer_belief\":\"خداحافظی کوتاه مراقبت است.\",\"reflect\":\"چه فکری می‌کنی؟\","
                + "\"practice\":{\"steps\":[\"یک کار ساختگی\"]},"
                + "\"check\":{\"question\":\"کدام؟\",\"options\":[\"الف\",\"ب\",\"ج\"],\"answer\":1}}";
        TeachCardTeacher.Fitted fitted = TeachCardTeacher.parse(good, lesson, d, 10);
        assertNotNull(fitted);
        assertTrue(fitted.fitted());
        assertEquals(1, fitted.answer());
        JsonNode content = JSON.readTree(fitted.contentJson());
        assertFalse(content.path("check").has("answer"), "the right option never goes into the card's content");
        assertEquals(lesson.practice().steps().get(0), content.path("practice").path("steps").get(0).asText(),
                "the recommended behavior is ours from official sources, not the teacher's");
        assertTrue(content.path("practice").path("sources").get(0).path("url").asText().startsWith("https://www.nhs.uk"));

        assertNull(TeachCardTeacher.parse("{\"schema\":\"teach-card.v2\",\"why_not\":[\"one\"]}", lesson, d, 10));
        TeachCardTeacher.Fitted fallback = TeachCardTeacher.fallback(lesson, d, 0, "test");
        JsonNode own = JSON.readTree(fallback.contentJson());
        assertEquals(lesson.belief(), own.path("belief").asText());
        assertEquals(d.lessonTheirWay, own.path("situation").asText());
    }
}
