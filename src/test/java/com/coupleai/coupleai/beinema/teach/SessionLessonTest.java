package com.coupleai.coupleai.beinema.teach;

import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Enum.IntentEpistemic;
import com.coupleai.coupleai.beinema.Enum.IntentStatus;
import com.coupleai.coupleai.beinema.counseling.SessionLessons;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The lessons after a session: the library, personal or general, the readings used, and the lock of an open exercise. */
class SessionLessonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void everyCompanionHasItsLessonsWithAWholeCheck() {
        for (AgentType type : List.of(AgentType.RELATIONSHIP, AgentType.FINANCIAL, AgentType.PARENTING,
                AgentType.PREMARITAL, AgentType.IN_LAWS, AgentType.SEXOLOGY)) {
            List<SessionLessons.Lesson> lessons = SessionLessons.of(type);
            assertTrue(lessons.size() >= 5, type + " has its general lessons");
            for (SessionLessons.Lesson lesson : lessons) {
                assertTrue(lesson.body().size() >= 2, lesson.id() + " explains in paragraphs");
                assertTrue(lesson.check().answer() < lesson.check().options().size(), lesson.id());
                assertFalse(lesson.fits().isBlank(), lesson.id() + " says which reading it answers");
            }
        }
        assertTrue(SessionLessons.all().stream().anyMatch(lesson -> lesson.exercise() != null && lesson.exercise().required()));
    }

    private static TeachCard given(long id, String lessonId, boolean openExercise) {
        TeachCard card = new TeachCard();
        card.setId(id);
        card.setChatRoomId(5L);
        card.setUserId(7L);
        card.setLessonId(lessonId);
        card.setKind(TeachCard.SESSION);
        card.setExerciseRequired(openExercise);
        return card;
    }

    private static User user() {
        User user = new User();
        user.setId(7L);
        user.setFirstName("سارا");
        return user;
    }

    private static SessionLessonService service(TeachCardRepository cards, SessionLessonWriter writer) {
        return new SessionLessonService(cards, null, null, null, null, null, null, null, writer);
    }

    @Test
    void withoutAConfirmedReadingTheNextGeneralLessonComes() {
        TeachCardRepository cards = mock(TeachCardRepository.class);
        when(cards.findAllByUserIdAndKindOrderByIdAsc(7L, TeachCard.SESSION)).thenReturn(List.of(given(1, "S-NINA-01", false)));
        SessionLessonWriter writer = mock(SessionLessonWriter.class);

        TeachCard card = service(cards, writer).lessonFor(user(), 5L, 2, AgentType.RELATIONSHIP,
                SessionLessons.of(AgentType.RELATIONSHIP), List.of());

        assertEquals("S-NINA-02", card.getLessonId());
        assertFalse(card.isFitted());
        assertEquals(TeachCard.SESSION, card.getKind());
        assertEquals(2, card.getSessionNumber());
        assertTrue(card.getExerciseRequired());
        verify(writer, never()).write(anyString(), anyString(), anyList(), anyList());
    }

    @Test
    void aConfirmedReadingMakesItPersonalAndAFailedWriterFallsBack() throws Exception {
        TeachCardRepository cards = mock(TeachCardRepository.class);
        when(cards.findAllByUserIdAndKindOrderByIdAsc(7L, TeachCard.SESSION)).thenReturn(List.of());
        SessionLessonWriter writer = mock(SessionLessonWriter.class);
        when(writer.write(anyString(), anyString(), anyList(), anyList())).thenReturn(new SessionLessonWriter.Written(
                "S-NINA-03", "گله‌ات را نرم شروع کن", "وقتی خسته‌ای، گله‌ات تند شروع می‌شود.",
                List.of("پاراگراف یک", "پاراگراف دو"), "مثال", "قدم", "تمرین تو", "چه شد؟"));

        TeachCard personal = service(cards, writer).lessonFor(user(), 5L, 1, AgentType.RELATIONSHIP,
                SessionLessons.of(AgentType.RELATIONSHIP), List.of("وقتی خسته است، گله را با سرزنش شروع می‌کند"));
        assertEquals("S-NINA-03", personal.getLessonId());
        assertTrue(personal.isFitted());
        JsonNode content = JSON.readTree(personal.getContentJson());
        assertEquals(SessionLessonService.SCHEMA, content.path("schema").asText());
        assertTrue(content.path("personal").asBoolean());
        assertEquals("تمرین تو", content.path("exercise").path("task").asText());
        // the small question stays the lesson's own: its right option is known
        assertEquals(SessionLessons.find("S-NINA-03").orElseThrow().check().question(), content.path("check").path("question").asText());
        assertEquals(SessionLessons.find("S-NINA-03").orElseThrow().check().answer(), personal.getCheckAnswer());

        when(writer.write(anyString(), anyString(), anyList(), anyList())).thenReturn(null);
        TeachCard general = service(cards, writer).lessonFor(user(), 5L, 1, AgentType.RELATIONSHIP,
                SessionLessons.of(AgentType.RELATIONSHIP), List.of("خوانشی"));
        assertEquals("S-NINA-01", general.getLessonId());
        assertFalse(general.isFitted());
    }

    @Test
    void whenAllAreGivenNoGeneralLessonRepeats() {
        TeachCardRepository cards = mock(TeachCardRepository.class);
        List<TeachCard> all = new ArrayList<>();
        SessionLessons.of(AgentType.IN_LAWS).forEach(lesson -> all.add(given(all.size() + 1, lesson.id(), false)));
        when(cards.findAllByUserIdAndKindOrderByIdAsc(7L, TeachCard.SESSION)).thenReturn(all);
        assertNull(service(cards, mock(SessionLessonWriter.class)).lessonFor(user(), 5L, 9, AgentType.IN_LAWS,
                SessionLessons.of(AgentType.IN_LAWS), List.of()));
    }

    @Test
    void everyLessonHasItsReviewerPartAndEachCompanionItsSpecializedLessons() {
        for (SessionLessons.Lesson lesson : SessionLessons.all()) {
            assertFalse(lesson.review().goal().isBlank(), lesson.id());
            assertFalse(lesson.review().basis().isBlank(), lesson.id());
            if (!lesson.general()) {
                assertFalse(lesson.prerequisites().isEmpty(), lesson.id() + " builds on a general lesson");
            }
        }
        for (AgentType type : List.of(AgentType.RELATIONSHIP, AgentType.FINANCIAL, AgentType.PARENTING,
                AgentType.PREMARITAL, AgentType.IN_LAWS, AgentType.SEXOLOGY)) {
            assertTrue(SessionLessons.of(type).stream().anyMatch(lesson -> !lesson.general()), type + " has specialized lessons");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSpecializedLessonIsOfferedOnlyAfterItsPrerequisitesWerePassed() {
        TeachCardRepository cards = mock(TeachCardRepository.class);
        SessionLessonWriter writer = mock(SessionLessonWriter.class);
        List<SessionLessons.Lesson> library = SessionLessons.of(AgentType.RELATIONSHIP);
        org.mockito.ArgumentCaptor<List<SessionLessons.Lesson>> offered = org.mockito.ArgumentCaptor.forClass(List.class);

        // S-NINA-02 given but neither its exercise nor its question answered: S-NINA-11 is not offered yet
        TeachCard given = given(1, "S-NINA-02", false);
        when(cards.findAllByUserIdAndKindOrderByIdAsc(7L, TeachCard.SESSION)).thenReturn(List.of(given));
        service(cards, writer).lessonFor(user(), 5L, 3, AgentType.RELATIONSHIP, library, List.of("خوانش"));
        verify(writer).write(anyString(), anyString(), anyList(), offered.capture());
        assertTrue(offered.getValue().stream().noneMatch(lesson -> lesson.id().equals("S-NINA-11")));

        given.setChosen(0);
        org.mockito.Mockito.reset(writer);
        service(cards, writer).lessonFor(user(), 5L, 3, AgentType.RELATIONSHIP, library, List.of("خوانش"));
        verify(writer).write(anyString(), anyString(), anyList(), offered.capture());
        assertTrue(offered.getValue().stream().anyMatch(lesson -> lesson.id().equals("S-NINA-11")));
    }

    @Test
    void withoutAReadingASpecializedLessonNeverComes() {
        TeachCardRepository cards = mock(TeachCardRepository.class);
        List<TeachCard> all = new ArrayList<>();
        SessionLessons.of(AgentType.PARENTING).stream().filter(SessionLessons.Lesson::general)
                .forEach(lesson -> {
                    TeachCard card = given(all.size() + 1, lesson.id(), false);
                    card.setChosen(0);
                    all.add(card);
                });
        when(cards.findAllByUserIdAndKindOrderByIdAsc(7L, TeachCard.SESSION)).thenReturn(all);
        assertNull(service(cards, mock(SessionLessonWriter.class)).lessonFor(user(), 5L, 9, AgentType.PARENTING,
                SessionLessons.of(AgentType.PARENTING), List.of()));
    }

    private static ClientIntent intent(Long user, IntentStatus status, IntentEpistemic epistemic, Integer session, String text) {
        ClientIntent intent = new ClientIntent();
        intent.setParticipantUserId(user);
        intent.setStatus(status);
        intent.setEpistemic(epistemic);
        intent.setSessionNumber(session);
        intent.setInterpretation(text);
        return intent;
    }

    @Test
    void onlyTheirOwnReadingsConfirmedByThemInThisSession() {
        List<String> readings = SessionLessonService.confirmed(List.of(
                intent(7L, IntentStatus.CONFIRMED, IntentEpistemic.CONFIRMED_BY_CLIENT, 3, "خودش تأیید کرد"),
                intent(7L, IntentStatus.CONFIRMED, IntentEpistemic.STATED, 3, "حرف خودش، نه برداشت"),
                intent(7L, IntentStatus.UNCONFIRMED, IntentEpistemic.HYPOTHESIS, 3, "حدس"),
                intent(8L, IntentStatus.CONFIRMED, IntentEpistemic.CONFIRMED_BY_CLIENT, 3, "مال دیگری"),
                intent(7L, IntentStatus.CONFIRMED, IntentEpistemic.CONFIRMED_BY_CLIENT, 1, "جلسهٔ قبل")),
                7L, 3, Instant.now());
        assertEquals(List.of("خودش تأیید کرد"), readings);
    }

    @Test
    void anOpenRequiredExerciseLocksTheNextLessonOfThatConversation() {
        TeachCard first = given(1, "S-NINA-01", true);
        TeachCard second = given(2, "S-NINA-02", true);
        assertFalse(TeachCardService.locked(first, List.of(first, second)));
        assertTrue(TeachCardService.locked(second, List.of(first, second)));
        first.setExerciseDoneAt(Instant.now());
        assertFalse(TeachCardService.locked(second, List.of(first, second)));

        TeachCard optional = given(3, "S-POURIA-03", false);
        TeachCard after = given(4, "S-POURIA-04", false);
        assertFalse(TeachCardService.locked(after, List.of(optional, after)), "an optional exercise locks nothing");
    }

    @Test
    void theWriterAnswerMustNameACandidateAndBeWhole() {
        List<SessionLessons.Lesson> candidates = SessionLessons.of(AgentType.RELATIONSHIP);
        assertNull(SessionLessonWriter.parse("{\"lesson_id\":\"NONE\"}", candidates));
        assertNull(SessionLessonWriter.parse("{\"lesson_id\":\"S-RAHA-01\",\"seen\":\"x\",\"body\":[\"a\",\"b\"],\"step\":\"s\"}", candidates));
        assertNull(SessionLessonWriter.parse("{\"lesson_id\":\"S-NINA-02\",\"seen\":\"x\",\"body\":[\"a\"],\"step\":\"s\"}", candidates));
        SessionLessonWriter.Written written = SessionLessonWriter.parse(
                "```json\n{\"lesson_id\":\"s-nina-02\",\"title\":\"\",\"seen\":\"دیدیم\",\"body\":[\"a\",\"b\"],\"step\":\"s\"}\n```", candidates);
        assertNotNull(written);
        assertEquals("S-NINA-02", written.lessonId());
        assertEquals(SessionLessons.find("S-NINA-02").orElseThrow().faTitle(), written.title());
        assertEquals(SessionLessons.find("S-NINA-02").orElseThrow().exercise().task(), written.exerciseTask(),
                "a missing exercise is the lesson's own");
    }

    @Test
    void theNightlyEmailShowsTheStepAndTheExercise() {
        TeachCard card = SessionLessonService.card(user(), 5L, 1, SessionLessons.find("S-NINA-03").orElseThrow(),
                AgentType.RELATIONSHIP, null);
        card.setId(9L);
        String html = LessonDigest.html(List.of(new LessonDigest.Due(card, false)), "u");
        assertTrue(html.contains("یک قدم کوچک"));
        assertTrue(html.contains("تمرین تا جلسهٔ بعد"));
        String reminder = LessonDigest.html(List.of(new LessonDigest.Due(card, true)), "u");
        assertTrue(reminder.contains("تمرین این درس هنوز منتظر توست"));
    }
}
