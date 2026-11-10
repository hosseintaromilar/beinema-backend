package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.SessionEndCheck;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Exception.QuestionAnswerException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.SessionEndCheckRepository;
import com.coupleai.coupleai.beinema.chat.ChatRoomEventHub;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The question "shall we end it here?". One "no" and the session goes on, and it is not
 * asked again in this session. It ends when everyone active says yes; the owner's yes is
 * enough when the other person is not here.
 */
@Service
@RequiredArgsConstructor
public class SessionEndCheckService {

    /** ended: the conversation closes now. continued: the companion goes on from the message. */
    public record Vote(boolean ended, boolean continued, Message message) {
    }

    private final SessionEndCheckRepository checkRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomEventHub eventHub;

    public boolean hasOpen(Long roomId) {
        return checkRepository.existsByChatRoomIdAndStatus(roomId, SessionEndCheck.OPEN);
    }

    public boolean askedInSession(Long roomId, int session) {
        return checkRepository.existsByChatRoomIdAndSessionNumber(roomId, session);
    }

    @Transactional
    public SessionEndCheck open(Long roomId, int session) {
        SessionEndCheck check = new SessionEndCheck();
        check.setPublicId(UUID.randomUUID().toString());
        check.setChatRoomId(roomId);
        check.setSessionNumber(session);
        check.setStatus(SessionEndCheck.OPEN);
        return checkRepository.save(check);
    }

    @Transactional
    public Vote vote(Long roomId, String publicId, boolean yes, User user) {
        ChatRoom room = member(roomId, user);
        SessionEndCheck check = checkRepository.findByPublicIdForUpdate(publicId)
                .filter(found -> found.getChatRoomId().equals(roomId))
                .orElseThrow(() -> new QuestionAnswerException("CHECK_NOT_FOUND", HttpStatus.NOT_FOUND, "این سؤال پیدا نشد."));
        if (!SessionEndCheck.OPEN.equals(check.getStatus())) {
            return new Vote(false, false, null);
        }
        if (!yes) {
            check.setStatus(SessionEndCheck.CONTINUED);
            checkRepository.save(check);
            int next = messageRepository.findMaxSequenceNumberByChatRoom(room).orElse(0) + 1;
            Message message = Message.builder()
                    .chatRoom(room)
                    .senderType(MessageSenderType.USER)
                    .senderId(user.getId())
                    .content("نه، می‌خوام ادامه بدیم.")
                    .status(MessageStatus.SENT)
                    .sequenceNumber(next)
                    .build();
            messageRepository.saveAndFlush(message);
            return new Vote(false, true, message);
        }
        Set<Long> said = ids(check);
        said.add(user.getId());
        check.setYesUsers(said.stream().map(String::valueOf).collect(Collectors.joining(",")));
        List<ChatRoomParticipant> active = ChatRoomParticipant.active(participantRepository.findAllByChatRoom(room));
        boolean everyone = active.stream().allMatch(person -> said.contains(person.getUser().getId()));
        boolean ownerAlone = user.getId().equals(room.getCreatedBy()) && active.stream()
                .filter(person -> !person.getUser().getId().equals(user.getId()))
                .noneMatch(person -> eventHub.isPresent(roomId, person.getUser().getId()));
        boolean ended = everyone || ownerAlone;
        if (ended) {
            check.setStatus(SessionEndCheck.ENDED);
        }
        checkRepository.save(check);
        return new Vote(ended, false, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> view(Long roomId, String publicId, User user) {
        ChatRoom room = member(roomId, user);
        SessionEndCheck check = checkRepository.findByPublicId(publicId)
                .filter(found -> found.getChatRoomId().equals(roomId))
                .orElseThrow(() -> new QuestionAnswerException("CHECK_NOT_FOUND", HttpStatus.NOT_FOUND, "این سؤال پیدا نشد."));
        Set<Long> said = ids(check);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", check.getPublicId());
        view.put("status", check.getStatus());
        view.put("iSaidYes", said.contains(user.getId()));
        view.put("waitingFor", ChatRoomParticipant.active(participantRepository.findAllByChatRoom(room)).stream()
                .filter(person -> !said.contains(person.getUser().getId()) && !person.getUser().getId().equals(user.getId()))
                .map(person -> person.getUser().visibleName())
                .findFirst()
                .orElse(""));
        return view;
    }

    private ChatRoom member(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new QuestionAnswerException("CHECK_NOT_FOUND", HttpStatus.NOT_FOUND, "این سؤال پیدا نشد."));
        if (!participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new QuestionAnswerException("NOT_MEMBER", HttpStatus.FORBIDDEN, "دسترسی به این گفتگو نداری.");
        }
        return room;
    }

    private static Set<Long> ids(SessionEndCheck check) {
        Set<Long> ids = new LinkedHashSet<>();
        if (check.getYesUsers() != null && !check.getYesUsers().isBlank()) {
            Arrays.stream(check.getYesUsers().split(",")).map(Long::valueOf).forEach(ids::add);
        }
        return ids;
    }
}
