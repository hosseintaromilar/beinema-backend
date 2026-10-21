package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.DTO.counseling.QuestionResponse;
import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Enum.QuestionStatus;

/** Maps a stored question to what the chat shows. */
public final class QuestionViews {

    private QuestionViews() {
    }

    public static QuestionResponse view(ClarifyingQuestion question, String addresseeName) {
        if (question == null || question.getStatus() == QuestionStatus.DRAFT) {
            return null;
        }
        return QuestionResponse.builder()
                .id(question.getPublicId())
                .type(question.getQuestionType().name())
                .text(question.getQuestionText())
                .options(QuestionOptions.read(question.getOptionsJson()).stream()
                        .map(option -> QuestionResponse.Option.builder()
                                .id(option.id())
                                .label(option.label())
                                .kind(option.kind())
                                .needsText(option.needsText())
                                .build())
                        .toList())
                .addresseeUserId(question.getParticipantUserId())
                .addresseeName(addresseeName)
                .status(question.getStatus().name())
                .selectedOptionId(question.getSelectedOptionId())
                .freeText(question.getFreeText())
                .answeredAt(question.getAnsweredAt())
                .build();
    }
}
