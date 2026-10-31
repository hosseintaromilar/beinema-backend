package com.coupleai.coupleai.beinema.DTO.messages;

import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.governance.CounselorSignal;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
import com.coupleai.coupleai.beinema.governance.QuestionMarkers;
import com.coupleai.coupleai.beinema.governance.ReentryCommitment;
import com.coupleai.coupleai.beinema.DTO.counseling.QuestionResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class MessageResponse {

    private Long id;

    private MessageSenderType senderType;

    private Long senderId;

    private Long participantId;

    private String participantRole;

    private String displayName;

    private String senderName;

    private String senderAvatar;

    private String content;

    private MessageStatus status;

    private Integer sequenceNumber;

    private LocalDateTime createdAt;

    private Long commitmentUserId;

    private String commitmentStatus;

    private String agreementKind;

    private String agentReaction;

    private Long replyToId;

    private String replyExcerpt;

    private String replyToSender;

    private java.util.List<AttachmentResponse> attachments;

    private Boolean sticker;

    private String stickerName;

    /** A card of the companion's kit shown by this message: TEST, RESULT or LESSON, and its id. */
    private String kitKind;
    private String kitRef;

    /** A structured clarifying question shown by this counselor message. */
    private QuestionResponse question;

    /** This partner message answers the structured question with this id. */
    private String answerToQuestionId;

    /** Set on a private notice: only this user sees the message. */
    private Long visibleToUserId;


    /**
     * Convert Message entity to MessageResponse DTO.
     */
    public static MessageResponse from(
            Message message
    ) {

        if (message == null) {
            return null;
        }


        return MessageResponse.builder()

                .id(
                        message.getId()
                )

                .senderType(
                        message.getSenderType()
                )

                .senderId(
                        message.getSenderId()
                )

                .displayName(
                        message.getSenderType() == MessageSenderType.AGENT
                                ? "مشاور"
                                : null
                )

                .content(
                        visibleContent(message.getContent())
                )

                .status(
                        message.getStatus()
                )

                .sequenceNumber(
                        message.getSequenceNumber()
                )

                .createdAt(
                        message.getCreatedAt()
                )

                .commitmentUserId(
                        ReentryCommitment.userId(message.getContent())
                )

                .commitmentStatus(
                        ReentryCommitment.status(message.getContent())
                )

                .agreementKind(
                        ReentryCommitment.kind(message.getContent())
                )
                .agentReaction(
                        message.getAgentReaction()
                )
                .replyToId(
                        message.getReplyToMessageId()
                )
                .sticker(
                        CounselorSignal.isSticker(message.getContent())
                )
                .stickerName(
                        CounselorSignal.stickerName(message.getContent())
                )
                .kitKind(
                        com.coupleai.coupleai.beinema.governance.KitMarkers.kind(message.getContent())
                )
                .kitRef(
                        com.coupleai.coupleai.beinema.governance.KitMarkers.ref(message.getContent())
                )
                .answerToQuestionId(
                        QuestionMarkers.answerTo(message.getContent())
                )
                .visibleToUserId(
                        message.getVisibleToUserId()
                )

                .build();
    }

    private static String visibleContent(String content) {
        return new GovernancePacketParser().strip(
                CounselorSignal.stripSticker(ReentryCommitment.visible(QuestionMarkers.strip(com.coupleai.coupleai.beinema.governance.KitMarkers.strip(content))))
        );
    }
}