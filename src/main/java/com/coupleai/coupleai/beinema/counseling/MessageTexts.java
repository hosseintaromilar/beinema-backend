package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.governance.CounselorSignal;
import com.coupleai.coupleai.beinema.governance.GovernancePacketParser;
import com.coupleai.coupleai.beinema.governance.QuestionMarkers;
import com.coupleai.coupleai.beinema.governance.ReentryCommitment;

/** The words of a stored message as the brain should read them, without control markers. */
public final class MessageTexts {

    private MessageTexts() {
    }

    public static String visible(Message message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        String content = message.getContent();
        if (CounselorSignal.isSticker(content)) {
            return "(sticker: " + CounselorSignal.stickerName(content) + ")";
        }
        boolean question = QuestionMarkers.questionId(content) != null;
        boolean answer = QuestionMarkers.answerTo(content) != null;
        String text = new GovernancePacketParser().strip(ReentryCommitment.visible(QuestionMarkers.strip(com.coupleai.coupleai.beinema.governance.KitMarkers.strip(content))));
        if (question) {
            return "(structured question) " + text;
        }
        if (answer) {
            return "(answer to a structured question) " + text.replace('\n', ' ');
        }
        return text;
    }

    /** Temporary debug lines and empty messages are not part of the conversation. */
    public static boolean countsAsConversation(Message message) {
        if (message == null || Boolean.TRUE.equals(message.getDeleted())) {
            return false;
        }
        if (message.getSenderType() == MessageSenderType.SYSTEM) {
            return false;
        }
        String content = message.getContent() == null ? "" : message.getContent().trim();
        return !content.startsWith("🔧");
    }
}
