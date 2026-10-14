package com.coupleai.coupleai.beinema.governance;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.GovernanceEventPayload;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.GovernanceLog;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Exception.CommitmentRequiredException;
import com.coupleai.coupleai.beinema.Exception.ParticipationPausedException;
import com.coupleai.coupleai.beinema.Exception.SessionEndedException;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.GovernanceLogRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import com.coupleai.coupleai.beinema.Service.CounselorSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ParticipationPauseService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationPauseService.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final GovernanceLogRepository governanceLogRepository;
    private final ChatRoomEventHub chatRoomEventHub;
    private final ChatRealtimeService chatRealtimeService;
    private final CounselorSessionService counselorSessionService;
    private final MessageRepository messageRepository;
    private final ParticipationPauseService self;
    private final GovernancePolicy policy;

    public ParticipationPauseService(
            ChatRoomRepository chatRoomRepository,
            ChatRoomParticipantRepository participantRepository,
            GovernanceLogRepository governanceLogRepository,
            ChatRoomEventHub chatRoomEventHub,
            ChatRealtimeService chatRealtimeService,
            GovernanceProperties properties,
            CounselorSessionService counselorSessionService,
            MessageRepository messageRepository,
            @Lazy ParticipationPauseService self
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
        this.governanceLogRepository = governanceLogRepository;
        this.chatRoomEventHub = chatRoomEventHub;
        this.chatRealtimeService = chatRealtimeService;
        this.counselorSessionService = counselorSessionService;
        this.messageRepository = messageRepository;
        this.self = self;
        this.policy = new GovernancePolicy(properties);
    }

    @Transactional
    public ResumeHint requireCanSend(ChatRoom chatRoom, User user) {
        if (chatRoom == null || user == null) {
            throw new SessionEndedException("جلسه پیدا نشد.");
        }
        if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
            throw new SessionEndedException("این جلسه دیگر فعال نیست.");
        }

        ChatRoomParticipant participant = participantRepository
                .findByChatRoomIdAndUserIdForUpdate(chatRoom.getId(), user.getId())
                .orElseThrow(() -> new RuntimeException("Access denied"));

        Instant previous = participant.getPausedUntil();
        boolean handshake = Boolean.TRUE.equals(participant.getReentryHandshake());
        if (handshake) {
            participant.setReentryHandshake(false);
            participantRepository.save(participant);
        }
        expireIfNeeded(participant);
        offerCommitmentIfDue(chatRoom, participant);
        offerSessionConsentIfDue(chatRoom, participant);
        if (Boolean.FALSE.equals(participant.getSessionConsented())) {
            throw new CommitmentRequiredException(
                    CommitmentRequiredException.SESSION_CONSENT,
                    "قبل از شروع گفتگو، لطفاً با قوانین گروه و چارچوب جلسه موافقت کن."
            );
        }
        if (policy.isActive(participant.getPausedUntil())) {
            throw new ParticipationPausedException(
                    "فعلاً امکان ارسال پیام در این جلسه را نداری.",
                    participant.getPausedUntil(),
                    policy.remainingSeconds(participant.getPausedUntil())
            );
        }
        if (Boolean.TRUE.equals(participant.getReentryPending())) {
            throw new CommitmentRequiredException(
                    "برای ادامه جلسه، لطفاً تعهد احترام به فرایند درمان را بپذیر."
            );
        }
        return new ResumeHint(handshake, previous);
    }

    @Transactional
    public void expireStale(ChatRoom chatRoom) {
        if (chatRoom == null) {
            return;
        }
        List<ChatRoomParticipant> participants =
                participantRepository.findAllByChatRoom(chatRoom);
        List<Long> awaiting = new ArrayList<>();
        for (ChatRoomParticipant participant : participants) {
            Instant previous = participant.getPausedUntil();
            if (expireIfNeeded(participant) && participant.getUser() != null) {
                if (offerCommitmentIfDue(chatRoom, participant)) {
                    awaiting.add(participant.getUser().getId());
                    audit(
                            chatRoom.getId(),
                            participant.getUser().getId(),
                            GovernanceAction.NONE,
                            GovernanceTarget.NONE,
                            0,
                            previous == null ? "ACTIVE" : "PAUSED",
                            "AWAITING_COMMITMENT",
                            "expired"
                    );
                }
            }
        }
        if (!awaiting.isEmpty()) {
            chatRoomEventHub.publishNamed(
                    chatRoom.getId(),
                    "commitment_required",
                    GovernanceEventPayload.builder()
                            .type("COMMITMENT_REQUIRED")
                            .chatRoomId(chatRoom.getId())
                            .action(GovernanceAction.NONE)
                            .target(GovernanceTarget.NONE)
                            .userIds(awaiting)
                            .reentryPending(true)
                            .message("برای ادامه، لطفاً تعهد احترام به جلسه را بپذیر.")
                            .build()
            );
        }
        List<Long> consentNeeded = new ArrayList<>();
        for (ChatRoomParticipant participant : participants) {
            if (offerSessionConsentIfDue(chatRoom, participant) && participant.getUser() != null) {
                consentNeeded.add(participant.getUser().getId());
            }
        }
        if (!consentNeeded.isEmpty()) {
            chatRoomEventHub.publishNamed(
                    chatRoom.getId(),
                    "session_consent_required",
                    GovernanceEventPayload.builder()
                            .type("SESSION_CONSENT_REQUIRED")
                            .chatRoomId(chatRoom.getId())
                            .action(GovernanceAction.NONE)
                            .target(GovernanceTarget.NONE)
                            .userIds(consentNeeded)
                            .sessionConsented(false)
                            .message("قبل از شروع، با چارچوب جلسه موافقت کن.")
                            .build()
            );
        }
    }

    @Transactional
    public ApplyResult apply(ChatRoom chatRoom, GovernanceDecision decision) {
        if (chatRoom == null || decision == null) {
            return ApplyResult.none();
        }
        if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
            log.info(
                    "governance skipped room={} action={} reason=session_inactive",
                    chatRoom.getId(),
                    decision.governanceAction()
            );
            return ApplyResult.none();
        }

        List<ChatRoomParticipant> locked =
                participantRepository.findAllByChatRoomIdForUpdate(chatRoom.getId());
        GovernancePolicy.Command command = policy.normalize(decision, locked, true);
        if (command.rejectReason() != null) {
            log.info(
                    "governance rejected room={} action={} reason={}",
                    chatRoom.getId(),
                    decision.governanceAction(),
                    command.rejectReason()
            );
            audit(
                    chatRoom.getId(),
                    null,
                    decision.governanceAction(),
                    decision.target(),
                    decision.durationSeconds(),
                    "ACTIVE",
                    "ACTIVE",
                    command.rejectReason()
            );
            return ApplyResult.none();
        }
        if (!command.shouldEnforce()) {
            if (decision.governanceAction() != null && decision.governanceAction() != GovernanceAction.NONE) {
                audit(
                        chatRoom.getId(),
                        null,
                        decision.governanceAction(),
                        decision.target(),
                        decision.durationSeconds(),
                        "ACTIVE",
                        "ACTIVE",
                        truncate(decision.reason())
                );
            }
            return ApplyResult.none();
        }

        if (command.endSession()) {
            ChatRoomStatus previous = chatRoom.getStatus();
            chatRoom.setStatus(ChatRoomStatus.ENDED);
            chatRoomRepository.saveAndFlush(chatRoom);
            for (ChatRoomParticipant participant : locked) {
                participant.setPausedUntil(null);
                participant.setReentryPending(false);
                participant.setCommitmentOffered(false);
                participant.setReentryHandshake(false);
                participantRepository.save(participant);
            }
            audit(
                    chatRoom.getId(),
                    null,
                    GovernanceAction.END_SESSION,
                    GovernanceTarget.BOTH,
                    0,
                    previous.name(),
                    ChatRoomStatus.ENDED.name(),
                    truncate(decision.reason())
            );
            chatRoomEventHub.publishNamed(
                    chatRoom.getId(),
                    "session_ended",
                    GovernanceEventPayload.builder()
                            .type("SESSION_ENDED")
                            .chatRoomId(chatRoom.getId())
                            .action(GovernanceAction.END_SESSION)
                            .target(GovernanceTarget.BOTH)
                            .userIds(command.userIds())
                            .message("این جلسه توسط مشاور به پایان رسید.")
                            .build()
            );
            return ApplyResult.none();
        }

        List<Long> pausedUsers = new ArrayList<>();
        for (ChatRoomParticipant participant : locked) {
            if (participant.getUser() == null
                    || !command.userIds().contains(participant.getUser().getId())) {
                continue;
            }
            Instant previous = participant.getPausedUntil();
            Instant merged = policy.mergePause(previous, command.pauseUntil());
            participant.setPausedUntil(merged);
            participant.setReentryPending(true);
            participant.setCommitmentOffered(false);
            participant.setReentryHandshake(false);
            participantRepository.save(participant);
            pausedUsers.add(participant.getUser().getId());
            audit(
                    chatRoom.getId(),
                    participant.getUser().getId(),
                    command.action(),
                    command.target(),
                    command.pauseUntil() == null
                            ? null
                            : (int) Math.max(1, command.pauseUntil().getEpochSecond() - Instant.now().getEpochSecond()),
                    policy.isActive(previous) ? "PAUSED" : "ACTIVE",
                    "PAUSED",
                    truncate(decision.reason())
            );
        }

        if (!pausedUsers.isEmpty()) {
            chatRoomEventHub.publishNamed(
                    chatRoom.getId(),
                    "participation_paused",
                    GovernanceEventPayload.builder()
                            .type("PARTICIPANT_PAUSED")
                            .chatRoomId(chatRoom.getId())
                            .action(command.action())
                            .target(command.target())
                            .userIds(pausedUsers)
                            .pausedUntil(command.pauseUntil() == null
                                    ? null
                                    : command.pauseUntil().toEpochMilli())
                            .remainingSeconds(policy.remainingSeconds(command.pauseUntil()))
                            .message("ارسال پیام برای آرام شدن فضای گفتگو موقتاً متوقف شد.")
                            .build()
            );
        }
        boolean bothPaused = command.action() == GovernanceAction.PAUSE_BOTH
                || pausedUsers.size() >= 2;
        return new ApplyResult(!pausedUsers.isEmpty(), bothPaused, command.action(), pausedUsers);
    }

    @Transactional
    public void acceptReentry(ChatRoom chatRoom, User user) {
        ChatRoomParticipant participant = lockedParticipant(chatRoom, user);
        expireIfNeeded(participant);
        if (policy.isActive(participant.getPausedUntil())) {
            throw new ParticipationPausedException(
                    "فعلاً امکان ارسال پیام در این جلسه را نداری.",
                    participant.getPausedUntil(),
                    policy.remainingSeconds(participant.getPausedUntil())
            );
        }
        if (!Boolean.TRUE.equals(participant.getReentryPending())) {
            return;
        }
        participant.setReentryPending(false);
        participant.setCommitmentOffered(false);
        participant.setReentryHandshake(true);
        participantRepository.save(participant);
        audit(
                chatRoom.getId(),
                user.getId(),
                GovernanceAction.NONE,
                GovernanceTarget.NONE,
                0,
                "AWAITING_COMMITMENT",
                "ACTIVE",
                "committed"
        );
        chatRoomEventHub.publishNamed(
                chatRoom.getId(),
                "participation_resumed",
                GovernanceEventPayload.builder()
                        .type("PARTICIPANT_RESUMED")
                        .chatRoomId(chatRoom.getId())
                        .action(GovernanceAction.NONE)
                        .target(GovernanceTarget.NONE)
                        .userIds(List.of(user.getId()))
                        .reentryPending(false)
                        .message("تعهد پذیرفته شد. می‌توانی به گفتگو برگردی.")
                        .build()
        );
    }

    @Transactional
    public Instant declineReentry(ChatRoom chatRoom, User user) {
        ChatRoomParticipant participant = lockedParticipant(chatRoom, user);
        expireIfNeeded(participant);
        Instant until = Instant.now().plusSeconds(30);
        Instant merged = policy.mergePause(participant.getPausedUntil(), until);
        participant.setPausedUntil(merged);
        participant.setReentryPending(true);
        participantRepository.save(participant);
        chatRoomEventHub.publishNamed(
                chatRoom.getId(),
                "participation_paused",
                GovernanceEventPayload.builder()
                        .type("PARTICIPANT_PAUSED")
                        .chatRoomId(chatRoom.getId())
                        .action(GovernanceAction.PAUSE_USER)
                        .target(GovernanceTarget.NONE)
                        .userIds(List.of(user.getId()))
                        .pausedUntil(merged.toEpochMilli())
                        .remainingSeconds(policy.remainingSeconds(merged))
                        .reentryPending(true)
                        .message("اشکالی ندارد. کمی دیگر صبر می‌کنیم تا آماده‌تر شوی.")
                        .build()
        );
        return merged;
    }

    @Transactional
    public void startConsentGate(ChatRoom chatRoom, User user) {
        if (chatRoom == null || user == null) {
            return;
        }
        ChatRoomParticipant participant = participantRepository
                .findByChatRoomAndUser(chatRoom, user)
                .orElse(null);
        if (participant == null) {
            return;
        }
        if (Boolean.TRUE.equals(participant.getSessionConsented())) {
            return;
        }
        participant.setSessionConsented(false);
        participantRepository.save(participant);
        offerSessionConsentIfDue(chatRoom, participant);
    }

    @Transactional
    public void acceptSessionConsent(ChatRoom chatRoom, User user) {
        ChatRoomParticipant participant = lockedParticipant(chatRoom, user);
        if (!Boolean.FALSE.equals(participant.getSessionConsented())) {
            return;
        }
        participant.setSessionConsented(true);
        participant.setConsentOffered(true);
        participantRepository.save(participant);
        audit(
                chatRoom.getId(),
                user.getId(),
                GovernanceAction.NONE,
                GovernanceTarget.NONE,
                0,
                "CONSENT_PENDING",
                "CONSENTED",
                "session_consent"
        );
        chatRoomEventHub.publishNamed(
                chatRoom.getId(),
                "session_consented",
                GovernanceEventPayload.builder()
                        .type("SESSION_CONSENTED")
                        .chatRoomId(chatRoom.getId())
                        .action(GovernanceAction.NONE)
                        .target(GovernanceTarget.NONE)
                        .userIds(List.of(user.getId()))
                        .sessionConsented(true)
                        .message("با چارچوب جلسه موافقت شد.")
                        .build()
        );
        maybeAnnounceSessionReady(chatRoom, user);
    }

    @Transactional
    public void declineSessionConsent(ChatRoom chatRoom, User user) {
        ChatRoomParticipant participant = lockedParticipant(chatRoom, user);
        participant.setSessionConsented(false);
        participantRepository.save(participant);
        if (chatRealtimeService != null) {
            chatRealtimeService.saveSystemAndPublish(
                    chatRoom.getId(),
                    "بدون پذیرش قوانین گروه و چارچوب مشاوره، امکان شروع گفتگو نیست. هر وقت آماده بودی، همان گزینه موافقت را بزن."
            );
        }
    }

    public boolean allParticipantsConsented(ChatRoom chatRoom) {
        List<ChatRoomParticipant> participants = participantRepository.findAllByChatRoom(chatRoom);
        if (participants.isEmpty()) {
            return false;
        }
        return participants.stream().allMatch(item -> !Boolean.FALSE.equals(item.getSessionConsented()));
    }

    @Scheduled(fixedDelay = 4000)
    @Transactional
    public void expireDuePauses() {
        List<Long> roomIds = participantRepository.findRoomIdsWithExpiredPause(Instant.now());
        for (Long roomId : roomIds) {
            chatRoomRepository.findById(roomId).ifPresent(this::expireStale);
        }
    }

    public Instant pausedUntil(ChatRoomParticipant participant) {
        if (participant == null || !policy.isActive(participant.getPausedUntil())) {
            return null;
        }
        return participant.getPausedUntil();
    }

    public boolean justResumed(ResumeHint hint) {
        return hint != null && hint.handshake();
    }

    private ChatRoomParticipant lockedParticipant(ChatRoom chatRoom, User user) {
        if (chatRoom == null || user == null) {
            throw new SessionEndedException("جلسه پیدا نشد.");
        }
        if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
            throw new SessionEndedException("این جلسه دیگر فعال نیست.");
        }
        return participantRepository
                .findByChatRoomIdAndUserIdForUpdate(chatRoom.getId(), user.getId())
                .orElseThrow(() -> new RuntimeException("Access denied"));
    }

    private boolean offerCommitmentIfDue(ChatRoom chatRoom, ChatRoomParticipant participant) {
        if (participant == null
                || participant.getUser() == null
                || !Boolean.TRUE.equals(participant.getReentryPending())
                || Boolean.TRUE.equals(participant.getCommitmentOffered())
                || policy.isActive(participant.getPausedUntil())) {
            return false;
        }
        participant.setCommitmentOffered(true);
        participantRepository.save(participant);
        if (chatRealtimeService != null) {
            chatRealtimeService.saveSystemAndPublish(
                    chatRoom.getId(),
                    ReentryCommitment.pendingContent(participant.getUser().getId())
            );
        }
        return true;
    }

    private boolean offerSessionConsentIfDue(ChatRoom chatRoom, ChatRoomParticipant participant) {
        if (participant == null
                || participant.getUser() == null
                || !Boolean.FALSE.equals(participant.getSessionConsented())
                || Boolean.TRUE.equals(participant.getConsentOffered())) {
            return false;
        }
        participant.setConsentOffered(true);
        participantRepository.save(participant);
        return true;
    }

    private void maybeAnnounceSessionReady(ChatRoom chatRoom, User user) {
        if (!allParticipantsConsented(chatRoom) || chatRealtimeService == null || user == null) {
            return;
        }
        Long roomId = chatRoom.getId();
        Long userId = user.getId();
        if (!TransactionSynchronizationManager.isSynchronizationActive() || self == null) {
            publishSessionOpening(roomId, userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                self.openSessionAfterCommit(roomId, userId);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void openSessionAfterCommit(Long roomId, Long userId) {
        publishSessionOpening(roomId, userId);
    }

    private void publishSessionOpening(Long roomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElse(null);
        if (chatRoom == null || chatRealtimeService == null) {
            return;
        }
        List<ChatRoomParticipant> members = participantRepository.findAllByChatRoom(chatRoom);
        String names = members.stream()
                .map(item -> item.getUser() == null ? "" : item.getUser().visibleName())
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + " و " + right)
                .orElse("");
        User speaker = members.stream()
                .map(ChatRoomParticipant::getUser)
                .filter(item -> item != null && userId.equals(item.getId()))
                .findFirst()
                .orElse(null);
        String spoken = askCounselorToOpen(chatRoom, speaker, names);
        Long agentId = 0L;
        if (counselorSessionService != null) {
            try {
                agentId = counselorSessionService.requireActiveAgent(chatRoom).getId();
            } catch (RuntimeException exception) {
                log.warn("No active counselor for room {}: {}", roomId, exception.getMessage());
            }
        }
        chatRealtimeService.saveAgentAndPublish(chatRoom.getId(), agentId, spoken);
    }

    private String askCounselorToOpen(ChatRoom chatRoom, User user, String names) {
        if (counselorSessionService == null || user == null) {
            return "";
        }
        try {
            ChatRoom bound = counselorSessionService.ensureBound(chatRoom, user);
            if (bound.getAiConversationId() == null || bound.getAiConversationId().isBlank()) {
                return "";
            }
            return counselorSessionService.requestEntry(
                    bound.getAiConversationId(),
                    activationCue(chatRoom, user, names)
            );
        } catch (RuntimeException exception) {
            log.warn("Could not open the session after consent: {}", exception.getMessage());
            return "";
        }
    }

    private String activationCue(ChatRoom chatRoom, User user, String names) {
        if (sessionAlreadyOpen(chatRoom)) {
            String arrived = user == null || user.visibleName() == null ? "" : user.visibleName();
            return EntryGreeting.participantConsented(arrived);
        }
        return EntryGreeting.sessionReady(names);
    }

    private boolean sessionAlreadyOpen(ChatRoom chatRoom) {
        if (messageRepository == null || chatRoom == null) {
            return false;
        }
        List<Message> messages = messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(chatRoom);
        // Entry greetings are counselor turns too, so only a partner's own message
        // proves the session has really started.
        return messages.stream()
                .anyMatch(message -> message.getSenderType() == MessageSenderType.USER);
    }

    private boolean expireIfNeeded(ChatRoomParticipant participant) {
        Instant until = participant.getPausedUntil();
        if (until != null && !policy.isActive(until)) {
            participant.setPausedUntil(null);
            participantRepository.save(participant);
            return true;
        }
        return false;
    }

    private void audit(
            Long roomId,
            Long userId,
            GovernanceAction action,
            GovernanceTarget target,
            Integer duration,
            String previous,
            String next,
            String reason
    ) {
        governanceLogRepository.save(GovernanceLog.builder()
                .chatRoomId(roomId)
                .participantUserId(userId)
                .action(action == null ? GovernanceAction.NONE : action)
                .target(target)
                .durationSeconds(duration)
                .previousState(previous)
                .newState(next)
                .reason(reason)
                .build());
        log.info(
                "governance room={} user={} action={} target={} duration={} previous={} next={}",
                roomId,
                userId,
                action,
                target,
                duration,
                previous,
                next
        );
    }

    private static String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180);
    }

    public record ResumeHint(boolean handshake, Instant previousPause) {
    }

    public record ApplyResult(
            boolean pauseEnforced,
            boolean bothPaused,
            GovernanceAction action,
            List<Long> userIds
    ) {
        public static ApplyResult none() {
            return new ApplyResult(false, false, GovernanceAction.NONE, List.of());
        }
    }
}
