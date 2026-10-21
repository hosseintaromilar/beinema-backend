package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.CounselingState;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Entity.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything one turn is decided from, loaded once: the room and partners, the
 * recent and the new messages, the memory, the recorded intents, the open and
 * just-answered questions, the last decisions, the safety screen, the situation
 * tags and the knowledge cards chosen for them.
 */
public record TurnMaterial(
        ChatRoom room,
        int sessionNumber,
        long minutesUsed,
        int minutesPlanned,
        List<Partner> partners,
        List<Message> recent,
        List<Message> fresh,
        List<TurnInbox.Note> notes,
        CounselingState state,
        CounselingSnapshot snapshot,
        List<ClientIntent> intents,
        List<ClarifyingQuestion> openQuestions,
        List<ClarifyingQuestion> answered,
        long questionsThisSession,
        List<DirectorDecisionRecord> lastTurns,
        SafetyScreen.Result screen,
        Set<String> tags,
        List<KbCard> cards,
        Map<Long, String> roleByUserId,
        Map<Long, String> roleByMessageId,
        Map<String, List<String>> looseTextsByRole,
        Map<Long, Message> messagesById,
        Map<Long, List<String>> fileLinesByMessageId,
        List<com.coupleai.coupleai.beinema.Entity.PersonMilestone> milestones
) {

    /** The stages of their own path each partner has reached (OWNER -> W, D...). */
    public Map<String, Set<String>> reachedStages() {
        Map<String, Set<String>> reached = new java.util.LinkedHashMap<>();
        for (var milestone : milestones) {
            reached.computeIfAbsent(milestone.getParticipantRole(), key -> new java.util.LinkedHashSet<>())
                    .add(milestone.getStage());
        }
        return reached;
    }

    public record Partner(String role, Long userId, String name, String status) {
    }

    public Long roomId() {
        return room.getId();
    }

    public boolean hasNewMessages() {
        return !fresh.isEmpty();
    }

    /** A moment of the session (it opened, a partner entered) waits to be decided about. */
    public boolean hasMoment() {
        return notes.stream().anyMatch(note -> note.messageId() == null);
    }

    public Long triggerMessageId() {
        return fresh.isEmpty() ? null : fresh.get(fresh.size() - 1).getId();
    }

    public String speakerRole() {
        return fresh.isEmpty() ? null : roleByUserId.get(fresh.get(fresh.size() - 1).getSenderId());
    }

    public Partner partner(String role) {
        return partners.stream().filter(partner -> partner.role().equals(role)).findFirst().orElse(null);
    }

    public String nameOf(String role) {
        Partner partner = partner(role);
        return partner == null || partner.name() == null ? role : partner.name();
    }

    public Long userIdOf(String role) {
        Partner partner = partner(role);
        return partner == null ? null : partner.userId();
    }

    public Set<String> partnerRoles() {
        return new java.util.LinkedHashSet<>(partners.stream().map(Partner::role).toList());
    }

    public String defaultAddressee() {
        String speaker = speakerRole();
        return speaker == null ? PartnerRole.BOTH : speaker;
    }

    public String roleOf(Message message) {
        if (message == null) {
            return "";
        }
        return roleByMessageId.getOrDefault(message.getId(), "");
    }
}
