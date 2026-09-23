package com.coupleai.coupleai.beinema.governance;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceFeatureTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
    private GovernancePacketParser parser;
    private GovernancePolicy policy;
    private ChatRoom room;
    private ChatRoomParticipant owner;
    private ChatRoomParticipant member;

    @BeforeEach
    void setUp() {
        parser = new GovernancePacketParser();
        GovernanceProperties properties = new GovernanceProperties();
        properties.setPauseMinSeconds(30);
        properties.setPauseMaxSeconds(300);
        properties.setPauseDefaultSeconds(90);
        policy = new GovernancePolicy(properties, clock);
        room = ChatRoom.builder().status(ChatRoomStatus.ACTIVE).createdBy(1L).title("s").build();
        room.setId(10L);
        User sara = user(1L, "سارا");
        User ali = user(2L, "علی");
        owner = participant(sara, ParticipantRole.OWNER);
        member = participant(ali, ParticipantRole.MEMBER);
    }

    @Nested
    class Parser {
        @Test
        void healthyWaitHasNoGovernance() {
            GovernanceDecision decision = parser.parse("");
            assertEquals(GovernanceAction.NONE, decision.governanceAction());
            assertTrue(decision.visibleText().isBlank());
        }

        @Test
        void noneMarkerIsHiddenFromTheReader() {
            GovernanceDecision decision = parser.parse(
                    "[[GOV|GOV=NONE|TARGET=NONE|DURATION=0]]\nاین جلسه مشاوره زوج است."
            );
            assertEquals(GovernanceAction.NONE, decision.governanceAction());
            assertFalse(decision.visibleText().contains("[[GOV"));
            assertEquals("این جلسه مشاوره زوج است.", decision.visibleText());
        }

        @Test
        void consentReplayDetectsAnotherAgreementAsk() {
            assertTrue(ConsentReplay.asksAgain(
                    "لطفاً بفرمایید که آیا با قوانین گروه و چارچوب مشاوره موافق هستید؟"
            ));
            assertFalse(ConsentReplay.asksAgain("حرفی که زدی را شنیدم. ادامه بده."));
        }

        @Test
        void listeningTextIsVisibleWithoutPause() {
            GovernanceDecision decision = parser.parse("ادامه بده، دارم گوش می‌دم.");
            assertEquals(GovernanceAction.NONE, decision.governanceAction());
            assertEquals("ادامه بده، دارم گوش می‌دم.", decision.visibleText());
        }

        @Test
        void markerPauseUser() {
            GovernanceDecision decision = parser.parse(
                    "[[GOV|ACTION=PAUSE_USER|TARGET=OWNER|DURATION=120]]\nالان ارسال پیام سارا موقتاً متوقف می‌شود."
            );
            assertEquals(GovernanceAction.PAUSE_USER, decision.governanceAction());
            assertEquals(GovernanceTarget.OWNER, decision.target());
            assertEquals(120, decision.durationSeconds());
            assertFalse(decision.visibleText().contains("[[GOV"));
            assertTrue(decision.visibleText().contains("سارا"));
        }

        @Test
        void commitmentMarkerIsStrippedFromVisibleText() {
            String raw = com.coupleai.coupleai.beinema.governance.ReentryCommitment.pendingContent(7L);
            assertEquals(7L, com.coupleai.coupleai.beinema.governance.ReentryCommitment.userId(raw));
            assertFalse(com.coupleai.coupleai.beinema.governance.ReentryCommitment.visible(raw).contains("[[COMMIT"));
        }

        @Test
        void consentMarkerIsStrippedFromVisibleText() {
            String raw = com.coupleai.coupleai.beinema.governance.ReentryCommitment.consentContent(8L);
            assertEquals(8L, com.coupleai.coupleai.beinema.governance.ReentryCommitment.userId(raw));
            assertEquals(
                    com.coupleai.coupleai.beinema.governance.ReentryCommitment.KIND_CONSENT,
                    com.coupleai.coupleai.beinema.governance.ReentryCommitment.kind(raw)
            );
            assertFalse(com.coupleai.coupleai.beinema.governance.ReentryCommitment.visible(raw).contains("[[AGREE"));
        }

        @Test
        void markerWithSessionActionAndPauseShapeIsPauseUser() {
            GovernanceDecision decision = parser.parse(
                    "[[GOV|ACTION=INTERVENE|TARGET=OWNER|DURATION=90]]\nارسال پیام موقتاً متوقف است."
            );
            assertEquals(GovernanceAction.PAUSE_USER, decision.governanceAction());
            assertEquals(GovernanceTarget.OWNER, decision.target());
            assertEquals(90, decision.durationSeconds());
            assertFalse(decision.visibleText().contains("[[GOV"));
        }

        @Test
        void markerGovFieldPauseUser() {
            GovernanceDecision decision = parser.parse(
                    "[[GOV|GOV=PAUSE_USER|TARGET=MEMBER|DURATION=60]]\nمکث موقت."
            );
            assertEquals(GovernanceAction.PAUSE_USER, decision.governanceAction());
            assertEquals(GovernanceTarget.MEMBER, decision.target());
        }

        @Test
        void directorPacketPauseBoth() {
            GovernanceDecision decision = parser.parse(
                    "ACTION=INTERVENE|PHASE=INTERVENTION|GOV=PAUSE_BOTH|TARGET=BOTH|DURATION=90|BRIEF=Reset the room"
            );
            assertEquals(GovernanceAction.PAUSE_BOTH, decision.governanceAction());
            assertEquals(GovernanceTarget.BOTH, decision.target());
            assertEquals("INTERVENE", decision.sessionAction());
            assertTrue(decision.visibleText().isBlank());
        }

        @Test
        void malformedUnknownActionIsFlagged() {
            GovernanceDecision decision = parser.parse("[[GOV|GOV=BAN_FOREVER|TARGET=OWNER|DURATION=12]]\nhi");
            assertTrue(decision.malformed() || decision.governanceAction() == GovernanceAction.NONE);
        }

        @Test
        void invalidJsonDoesNotCrash() {
            GovernanceDecision decision = parser.parse("{not json");
            assertEquals(GovernanceAction.NONE, decision.governanceAction());
            assertEquals("{not json", decision.visibleText());
        }
    }

    @Nested
    class Policy {
        @Test
        void normalDisagreementDoesNotPause() {
            GovernanceDecision decision = GovernanceDecision.none("هر دو ناراحتید. ادامه بدهید.");
            GovernancePolicy.Command command = policy.normalize(decision, List.of(owner, member), true);
            assertFalse(command.shouldEnforce());
        }

        @Test
        void angerWithoutInsultDoesNotPause() {
            GovernanceDecision decision = new GovernanceDecision(
                    "REFLECT", GovernanceAction.NONE, GovernanceTarget.NONE, 0, null, "خسته‌ای.", false
            );
            assertFalse(policy.normalize(decision, List.of(owner, member), true).shouldEnforce());
        }

        @Test
        void pauseUserRequiresExactMember() {
            GovernanceDecision decision = new GovernanceDecision(
                    "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.OWNER, 120, "insult", "stop", false
            );
            GovernancePolicy.Command command = policy.normalize(decision, List.of(owner, member), true);
            assertEquals(List.of(1L), command.userIds());
            assertEquals(clock.instant().plusSeconds(120), command.pauseUntil());
        }

        @Test
        void pauseDoesNotTargetTheOtherPartner() {
            GovernanceDecision decision = new GovernanceDecision(
                    "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.OWNER, 60, "insult", "stop", false
            );
            GovernancePolicy.Command command = policy.normalize(decision, List.of(owner, member), true);
            assertFalse(command.userIds().contains(2L));
        }

        @Test
        void pauseBothTargetsEveryone() {
            GovernanceDecision decision = new GovernanceDecision(
                    "INTERVENE", GovernanceAction.PAUSE_BOTH, GovernanceTarget.BOTH, 90, "fight", "pause", false
            );
            GovernancePolicy.Command command = policy.normalize(decision, List.of(owner, member), true);
            assertEquals(2, command.userIds().size());
        }

        @Test
        void pauseUserWithBothTargetIsRejected() {
            GovernanceDecision decision = new GovernanceDecision(
                    "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.BOTH, 60, "x", "x", false
            );
            assertEquals("target_invalid", policy.normalize(decision, List.of(owner, member), true).rejectReason());
        }

        @Test
        void missingTargetIsRejected() {
            GovernanceDecision decision = new GovernanceDecision(
                    "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.NONE, 60, "x", "x", false
            );
            assertEquals("target_invalid", policy.normalize(decision, List.of(owner, member), true).rejectReason());
        }

        @Test
        void targetOutsideSessionIsRejected() {
            GovernanceDecision decision = new GovernanceDecision(
                    "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.MEMBER, 60, "x", "x", false
            );
            assertEquals("target_not_in_session", policy.normalize(decision, List.of(owner), true).rejectReason());
        }

        @Test
        void durationIsClamped() {
            assertEquals(30, policy.clampDuration(1));
            assertEquals(300, policy.clampDuration(99999999));
            assertEquals(90, policy.clampDuration(null));
            assertEquals(90, policy.clampDuration(-10));
            assertEquals(120, policy.clampDuration(120));
        }

        @Test
        void inactiveSessionIsIgnored() {
            GovernanceDecision decision = new GovernanceDecision(
                    "END_SESSION", GovernanceAction.END_SESSION, GovernanceTarget.BOTH, 0, "x", "x", false
            );
            assertEquals("session_inactive", policy.normalize(decision, List.of(owner, member), false).rejectReason());
        }

        @Test
        void malformedDecisionIsIgnored() {
            GovernanceDecision decision = new GovernanceDecision(
                    "INTERVENE", GovernanceAction.PAUSE_USER, GovernanceTarget.OWNER, 60, "x", "x", true
            );
            assertEquals("malformed", policy.normalize(decision, List.of(owner, member), true).rejectReason());
        }

        @Test
        void laterPauseWinsWhenMerged() {
            Instant first = clock.instant().plusSeconds(30);
            Instant second = clock.instant().plusSeconds(120);
            assertEquals(second, policy.mergePause(first, second));
            assertEquals(second, policy.mergePause(second, first));
        }

        @Test
        void cautionDoesNotChangeAccess() {
            GovernanceDecision decision = new GovernanceDecision(
                    "INTERVENE", GovernanceAction.CAUTION, GovernanceTarget.OWNER, 0, "warn", "warn", false
            );
            assertFalse(policy.normalize(decision, List.of(owner, member), true).shouldEnforce());
        }
    }

    @Nested
    class StreamBuffer {
        @Test
        void hidesMarkerUntilCompleteThenEmitsSpeech() {
            GovStreamBuffer buffer = new GovStreamBuffer();
            assertEquals("", buffer.accept("[[GOV|ACTION=PAUSE_USER|TAR"));
            String visible = buffer.accept("GET=OWNER|DURATION=90]]\nحالا مکث می‌کنیم.");
            assertTrue(visible.contains("مکث"));
            assertFalse(visible.contains("[[GOV"));
            assertEquals(GovernanceAction.PAUSE_USER, buffer.finish().governanceAction());
        }

        @Test
        void waitStaysSilent() {
            GovStreamBuffer buffer = new GovStreamBuffer();
            buffer.accept("");
            assertTrue(buffer.finish().visibleText().isBlank());
        }
    }

    @Nested
    class ScenarioMatrix {
        @Test
        void ordinaryDisagreementStaysOpen() {
            assertNoPause("این موضوع برای هر دوتاتون سخته. علی، تو چه حسی داری؟");
        }

        @Test
        void angerWithoutAbuseStaysOpen() {
            assertNoPause("عصبانیتت قابل فهمه. بگو این خستگی از کجا می‌آد.");
        }

        @Test
        void singleInsultWithoutGovMarkerDoesNotAutoPause() {
            assertNoPause("این حرف تحقیرآمیز بود. می‌تونیم بدون توهین ادامه بدیم؟");
        }

        @Test
        void repeatedInsultCanPauseOneUser() {
            GovernanceDecision decision = parser.parse(
                    "[[GOV|ACTION=PAUSE_USER|TARGET=OWNER|DURATION=90]]\nسارا، ارسال پیام برای دو دقیقه متوقف می‌شود."
            );
            GovernancePolicy.Command command = policy.normalize(decision, List.of(owner, member), true);
            assertTrue(command.shouldEnforce());
            assertEquals(List.of(1L), command.userIds());
        }

        @Test
        void bothEscalatingPausesBoth() {
            GovernanceDecision decision = parser.parse(
                    "[[GOV|ACTION=PAUSE_BOTH|TARGET=BOTH|DURATION=120]]\nهر دو نفر برای آرام شدن مکث می‌کنید."
            );
            assertEquals(2, policy.normalize(decision, List.of(owner, member), true).userIds().size());
        }

        @Test
        void endSessionClosesRoom() {
            GovernanceDecision decision = parser.parse(
                    "[[GOV|ACTION=END_SESSION|TARGET=BOTH|DURATION=0]]\nجلسه را اینجا تمام می‌کنیم."
            );
            GovernancePolicy.Command command = policy.normalize(decision, List.of(owner, member), true);
            assertTrue(command.endSession());
        }
    }

    private void assertNoPause(String text) {
        GovernanceDecision decision = parser.parse(text);
        assertFalse(policy.normalize(decision, List.of(owner, member), true).shouldEnforce());
    }

    private static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }

    private ChatRoomParticipant participant(User user, ParticipantRole role) {
        ChatRoomParticipant participant = ChatRoomParticipant.builder()
                .chatRoom(room)
                .user(user)
                .role(role)
                .build();
        participant.setId(user.getId());
        return participant;
    }
}
