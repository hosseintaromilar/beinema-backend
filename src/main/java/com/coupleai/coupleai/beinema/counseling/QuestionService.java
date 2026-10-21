package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.DTO.counseling.AnswerQuestionRequest;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Enum.QuestionStatus;
import com.coupleai.coupleai.beinema.Enum.QuestionType;
import com.coupleai.coupleai.beinema.Exception.QuestionAnswerException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.ClarifyingQuestionRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Service.SessionLifecycleService;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import com.coupleai.coupleai.beinema.governance.QuestionMarkers;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structured clarifying questions: created as a draft with the decision,
 * opened when the counselor's message is sent, answered once by the partner it
 * is addressed to. An answer is a real event: it is stored on the question,
 * becomes a message in the conversation, and updates the readings it decides.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    static final int MAX_FREE_TEXT = 1000;

    private final ClarifyingQuestionRepository questionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final IntentLedger intentLedger;
    private final SessionLifecycleService sessionLifecycleService;
    private final ParticipationPauseService participationPauseService;

    public record AnswerResult(ClarifyingQuestion question, Message answerMessage, boolean repeated) {
    }

    @Transactional
    public ClarifyingQuestion createDraft(Long roomId, Long decisionId, DirectorDecision.QuestionSpec spec,
                                          Long addresseeUserId, List<Long> intentIds, int session) {
        ClarifyingQuestion question = new ClarifyingQuestion();
        question.setPublicId(UUID.randomUUID().toString());
        question.setChatRoomId(roomId);
        question.setParticipantUserId(addresseeUserId);
        question.setParticipantRole(spec.addressee);
        question.setDecisionId(decisionId);
        question.setQuestionType(QuestionType.valueOf(spec.type));
        question.setTargetIntentIds(IntentLedger.csv(intentIds));
        question.setReason(spec.reason);
        question.setDecisionImpact(spec.impact);
        question.setStatus(QuestionStatus.DRAFT);
        question.setSessionNumber(session);
        return questionRepository.save(question);
    }

    /** Puts the voice's words on the question and opens it; older open questions of the same partner expire. */
    @Transactional
    public List<ClarifyingQuestion> open(Long questionId, String text, List<QuestionOptions.Option> options) {
        ClarifyingQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalStateException("Question not found: " + questionId));
        List<ClarifyingQuestion> expired = new ArrayList<>();
        for (ClarifyingQuestion older : questionRepository.findAllByChatRoomIdAndStatus(question.getChatRoomId(), QuestionStatus.OPEN)) {
            if (older.getParticipantUserId().equals(question.getParticipantUserId())) {
                older.setStatus(QuestionStatus.EXPIRED);
                older.setStatusReason("a newer question was asked");
                questionRepository.save(older);
                expired.add(older);
            }
        }
        question.setQuestionText(text);
        question.setOptionsJson(QuestionOptions.toJson(options));
        question.setStatus(QuestionStatus.OPEN);
        questionRepository.save(question);
        return expired;
    }

    @Transactional
    public void attachMessage(Long questionId, Long messageId) {
        questionRepository.findById(questionId).ifPresent(question -> {
            question.setMessageId(messageId);
            questionRepository.save(question);
        });
    }

    @Transactional
    public void cancel(Long questionId, String reason) {
        questionRepository.findById(questionId).ifPresent(question -> {
            if (question.getStatus() == QuestionStatus.DRAFT || question.getStatus() == QuestionStatus.OPEN) {
                question.setStatus(QuestionStatus.CANCELLED);
                question.setStatusReason(clip(reason, 300));
                questionRepository.save(question);
            }
        });
    }

    /** The director closed open questions: answered in free text, or no longer fitting. */
    @Transactional
    public List<ClarifyingQuestion> applyUpdates(Long roomId, List<DirectorDecision.QuestionUpdate> updates, Long decisionId) {
        List<ClarifyingQuestion> changed = new ArrayList<>();
        for (DirectorDecision.QuestionUpdate update : updates) {
            questionRepository.findById(update.id)
                    .filter(question -> question.getChatRoomId().equals(roomId) && question.getStatus() == QuestionStatus.OPEN)
                    .ifPresent(question -> {
                        boolean answered = "ANSWERED_IN_TEXT".equals(update.status);
                        question.setStatus(answered ? QuestionStatus.ANSWERED : QuestionStatus.EXPIRED);
                        question.setStatusReason(clip((answered ? "answered in free text" : "expired") + " (d" + decisionId + ") "
                                + update.reason, 300));
                        if (answered) {
                            question.setAnsweredAt(LocalDateTime.now());
                        }
                        questionRepository.save(question);
                        changed.add(question);
                    });
        }
        return changed;
    }

    /** Questions of earlier sessions are not left hanging into a new one. */
    @Transactional
    public List<ClarifyingQuestion> expireStale(Long roomId, int session) {
        List<ClarifyingQuestion> expired = new ArrayList<>();
        for (ClarifyingQuestion question : questionRepository.findAllByChatRoomIdAndStatus(roomId, QuestionStatus.OPEN)) {
            if (question.getSessionNumber() != null && question.getSessionNumber() < session) {
                question.setStatus(QuestionStatus.EXPIRED);
                question.setStatusReason("the session ended");
                questionRepository.save(question);
                expired.add(question);
            }
        }
        return expired;
    }

    @Transactional
    public AnswerResult answer(Long roomId, String publicId, AnswerQuestionRequest request, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new QuestionAnswerException("QUESTION_NOT_FOUND", HttpStatus.NOT_FOUND, "سؤال پیدا نشد."));
        if (!participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new QuestionAnswerException("NOT_MEMBER", HttpStatus.FORBIDDEN, "دسترسی به این گفتگو نداری.");
        }
        ClarifyingQuestion question = questionRepository.findByPublicIdForUpdate(publicId)
                .filter(found -> found.getChatRoomId().equals(roomId))
                .orElseThrow(() -> new QuestionAnswerException("QUESTION_NOT_FOUND", HttpStatus.NOT_FOUND, "سؤال پیدا نشد."));
        String requestId = request == null ? null : clip(request.getClientRequestId(), 64);

        if (question.getStatus() == QuestionStatus.ANSWERED) {
            boolean sameSubmit = requestId != null && requestId.equals(question.getClientRequestId())
                    && user.getId().equals(question.getParticipantUserId());
            if (sameSubmit) {
                Message previous = question.getAnswerMessageId() == null ? null
                        : messageRepository.findById(question.getAnswerMessageId()).orElse(null);
                return new AnswerResult(question, previous, true);
            }
            throw new QuestionAnswerException("ALREADY_ANSWERED", HttpStatus.CONFLICT, "این سؤال قبلاً پاسخ داده شده.");
        }
        if (question.getStatus() != QuestionStatus.OPEN) {
            throw new QuestionAnswerException("QUESTION_CLOSED", HttpStatus.CONFLICT, "این سؤال دیگر فعال نیست.");
        }
        if (!user.getId().equals(question.getParticipantUserId())) {
            throw new QuestionAnswerException("NOT_ADDRESSEE", HttpStatus.FORBIDDEN, "این سؤال از نفر دیگری پرسیده شده.");
        }
        QuestionOptions.Option option = QuestionOptions.find(question, request == null ? null : request.getOptionId());
        if (option == null) {
            throw new QuestionAnswerException("INVALID_OPTION", HttpStatus.BAD_REQUEST, "یکی از گزینه‌ها را انتخاب کن.");
        }
        String freeText = request.getFreeText() == null ? "" : request.getFreeText().trim();
        if (freeText.length() > MAX_FREE_TEXT) {
            throw new QuestionAnswerException("TEXT_TOO_LONG", HttpStatus.BAD_REQUEST, "توضیح خیلی طولانی است.");
        }
        if (option.needsText() && freeText.length() < 2) {
            throw new QuestionAnswerException("TEXT_REQUIRED", HttpStatus.BAD_REQUEST, "لطفاً با چند کلمه بگو منظورت چیست.");
        }
        sessionLifecycleService.requireActive(room);
        participationPauseService.requireCanSend(room, user);

        // the answer is also part of the conversation
        int next = messageRepository.findMaxSequenceNumberByChatRoom(room).orElse(0) + 1;
        Message message = Message.builder()
                .chatRoom(room)
                .senderType(MessageSenderType.USER)
                .senderId(user.getId())
                .content(QuestionMarkers.answerContent(publicId, option.label(), freeText))
                .status(MessageStatus.SENT)
                .sequenceNumber(next)
                .build();
        messageRepository.saveAndFlush(message);

        IntentLedger.Resolution resolution = intentLedger.resolveFromAnswer(question, option);
        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("confirmed", resolution.confirmed().stream().map(id -> "i" + id).toList());
        applied.put("rejected", resolution.rejected().stream().map(id -> "i" + id).toList());
        applied.put("note", resolution.note());

        question.setStatus(QuestionStatus.ANSWERED);
        question.setAnsweredAt(LocalDateTime.now());
        question.setSelectedOptionId(option.id());
        question.setFreeText(freeText.isEmpty() ? null : freeText);
        question.setAnswerMessageId(message.getId());
        question.setClientRequestId(requestId);
        question.setResolutionJson(CounselingStateStore.json(applied));
        questionRepository.save(question);
        return new AnswerResult(question, message, false);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
