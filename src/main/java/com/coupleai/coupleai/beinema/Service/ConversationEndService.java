package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Feedback.FeedbackRequest;
import com.coupleai.coupleai.beinema.DTO.Feedback.FeedbackView;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.ServiceFeedback;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomMemberStatus;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Exception.ChatRoomAccessDeniedException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.ServiceFeedbackRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.counseling.PartnerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ending a conversation for good, coming back to it, and the short survey.
 * <ul>
 *   <li>The owner ends it: the room is ENDED. Nobody writes any more and the
 *       counselor stops; both still read it until the owner deletes it.</li>
 *   <li>The other person ends it: they LEFT. They read but do not write and are
 *       not counted in sessions or turns; the owner (who pays) can go on alone,
 *       or end it too. They can come back.</li>
 * </ul>
 * Not one transaction on purpose: the room is ENDED in the database before the
 * open session is closed, which the closing (run in the background) reads.
 */
@Service
@RequiredArgsConstructor
public class ConversationEndService {

    static final String OWNER_ENDED_TEXT =
            "%s این گفتگو را به‌طور کامل پایان داد. از این به بعد امکان نوشتن پیام نیست؛ پیام‌های قبلی همچنان قابل دیدن است.";
    static final String MEMBER_LEFT_TEXT = "%s از ادامهٔ این گفتگو کنار کشید.";
    static final String MEMBER_BACK_TEXT = "%s به گفتگو برگشت.";
    static final int MAX_COMMENT = 2000;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ServiceFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ChatRealtimeService chatRealtimeService;
    private final SessionLifecycleService sessionLifecycleService;
    private final ApplicationEventPublisher events;

    /** What happened, for the page: ENDED (the owner closed it) or LEFT (this person left). */
    public record EndResult(String outcome) {
    }

    public EndResult end(Long roomId) {
        User user = currentUserService.getCurrentUser();
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));
        ChatRoomParticipant mine = participantRepository.findByChatRoomAndUser(room, user)
                .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));

        if (room.getStatus() == ChatRoomStatus.ENDED) {
            return new EndResult("ENDED");
        }
        if (isOwner(room, mine)) {
            room.setStatus(ChatRoomStatus.ENDED);
            room.setEndedAt(Instant.now());
            room.setEndedBy(user.getId());
            chatRoomRepository.save(room);
            chatRealtimeService.saveSystemAndPublish(room.getId(), String.format(OWNER_ENDED_TEXT, user.visibleName()));
            sessionLifecycleService.closeForGood(room, user);
            return new EndResult("ENDED");
        }

        if (!mine.isActiveMember()) {
            return new EndResult("LEFT");
        }
        mine.setMemberStatus(ChatRoomMemberStatus.LEFT);
        mine.setLeftAt(Instant.now());
        mine.setSessionReadyAt(null);
        mine.setBreakVote(null);
        participantRepository.save(mine);
        chatRealtimeService.saveSystemAndPublish(room.getId(), String.format(MEMBER_LEFT_TEXT, user.visibleName()));
        String owner = ownerLabel(room);
        events.publishEvent(new SessionMoment(room.getId(), user.getId(),
                "[SYSTEM | SESSION] PARTNER_LEFT\n"
                        + label(mine) + " ended the conversation for themselves and left it. They still read it and may come back.\n"
                        + "From now on the work goes on with " + owner + " only: their own wants, their own doing, their own steps; "
                        + "do not speak for the one who left or about what they would want.\n"
                        + "Write one short, warm paragraph to " + owner + ": take it in without blame, and say they can go on here "
                        + "with their own part of the work, or end the conversation too (the buttons are on their screen).",
                null));
        return new EndResult("LEFT");
    }

    public void rejoin(Long roomId) {
        User user = currentUserService.getCurrentUser();
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));
        ChatRoomParticipant mine = participantRepository.findByChatRoomAndUser(room, user)
                .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));
        if (room.getStatus() == ChatRoomStatus.ENDED) {
            throw new IllegalArgumentException("این گفتگو به‌طور کامل پایان یافته و برگشت به آن ممکن نیست.");
        }
        if (mine.isActiveMember()) {
            return;
        }
        mine.setMemberStatus(ChatRoomMemberStatus.ACTIVE);
        mine.setLeftAt(null);
        participantRepository.save(mine);
        chatRealtimeService.saveSystemAndPublish(room.getId(), String.format(MEMBER_BACK_TEXT, user.visibleName()));
        events.publishEvent(new SessionMoment(room.getId(), user.getId(),
                "[SYSTEM | SESSION] PARTNER_REJOINED\n"
                        + label(mine) + " came back to the conversation after leaving it; the work is for both of them again.\n"
                        + "Write a short, warm welcome back to them by name. This is not their first entry: no rules, no "
                        + "introduction of yourself. In one or two sentences say where the work stands now, then how you go on "
                        + "with both of them.",
                null));
    }

    public void saveFeedback(Long roomId, FeedbackRequest request) {
        User user = currentUserService.getCurrentUser();
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));
        ChatRoomParticipant mine = participantRepository.findByChatRoomAndUser(room, user)
                .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));
        if (request == null) {
            throw new IllegalArgumentException("پاسخی فرستاده نشد.");
        }
        ServiceFeedback feedback = feedbackRepository.findByChatRoomIdAndUserId(roomId, user.getId())
                .orElseGet(ServiceFeedback::new);
        feedback.setChatRoomId(roomId);
        feedback.setUserId(user.getId());
        feedback.setRole(isOwner(room, mine) ? "OWNER" : "MEMBER");
        feedback.setHeard(score(request.getHeard()));
        feedback.setFairness(score(request.getFairness()));
        feedback.setProgress(score(request.getProgress()));
        feedback.setEase(score(request.getEase()));
        feedback.setRecommend(score(request.getRecommend()));
        String comment = request.getComment() == null ? "" : request.getComment().trim();
        if (comment.length() > MAX_COMMENT) {
            throw new IllegalArgumentException("متن نظر خیلی طولانی است.");
        }
        feedback.setComment(comment.isEmpty() ? null : comment);
        boolean empty = feedback.getHeard() == null && feedback.getFairness() == null && feedback.getProgress() == null
                && feedback.getEase() == null && feedback.getRecommend() == null && feedback.getComment() == null;
        if (empty) {
            throw new IllegalArgumentException("دست‌کم به یک سؤال جواب بده.");
        }
        feedbackRepository.save(feedback);
    }

    /** For the admin panel, newest first. */
    public List<FeedbackView> allFeedback() {
        List<ServiceFeedback> all = feedbackRepository.findAllByOrderByIdDesc();
        Map<Long, String> titles = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        return all.stream().map(item -> new FeedbackView(
                item.getId(),
                item.getChatRoomId(),
                titles.computeIfAbsent(item.getChatRoomId(), id ->
                        chatRoomRepository.findById(id).map(ChatRoom::getTitle).orElse("(حذف‌شده)")),
                names.computeIfAbsent(item.getUserId(), id ->
                        userRepository.findById(id).map(User::visibleName).orElse("-")),
                item.getRole(),
                item.getHeard(),
                item.getFairness(),
                item.getProgress(),
                item.getEase(),
                item.getRecommend(),
                item.getComment(),
                item.getCreatedAt() == null ? null
                        : item.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )).toList();
    }

    static boolean isOwner(ChatRoom room, ChatRoomParticipant mine) {
        return Objects.equals(room.getCreatedBy(), mine.getUser().getId())
                || mine.getRole() == ParticipantRole.OWNER
                || mine.getRole() == ParticipantRole.PARTNER_A;
    }

    private static Integer score(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("پاسخ هر سؤال باید یکی از گزینه‌ها باشد.");
        }
        return value;
    }

    private static String label(ChatRoomParticipant person) {
        return PartnerRole.of(person.getRole()) + " (" + person.getUser().visibleName() + ")";
    }

    private String ownerLabel(ChatRoom room) {
        return participantRepository.findAllByChatRoom(room).stream()
                .filter(person -> person.getUser() != null && isOwner(room, person))
                .findFirst()
                .map(ConversationEndService::label)
                .orElse("OWNER");
    }
}
