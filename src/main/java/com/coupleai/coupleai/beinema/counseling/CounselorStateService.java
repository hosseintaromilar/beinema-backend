package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.CounselingState;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Exception.ChatRoomAccessDeniedException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.ClarifyingQuestionRepository;
import com.coupleai.coupleai.beinema.Repository.ClientIntentRepository;
import com.coupleai.coupleai.beinema.Repository.DirectorDecisionRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The counselor's inner state for the developer panel: memory, intents,
 * questions and the last decisions with their reasons. Only for admins, or for
 * room members when beinema.debug.state-panel=true (development).
 */
@Service
@RequiredArgsConstructor
public class CounselorStateService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final CounselingStateStore stateStore;
    private final ClientIntentRepository intentRepository;
    private final ClarifyingQuestionRepository questionRepository;
    private final DirectorDecisionRecordRepository decisionRepository;
    private final CounselingConfig config;

    public boolean canView(User user) {
        return user != null && (user.getRole() == UserRole.ADMIN || config.isStatePanel());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> describe(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomAccessDeniedException("Access denied"));
        boolean member = participantRepository.existsByChatRoomAndUser(room, user);
        if (!canView(user) || (!member && user.getRole() != UserRole.ADMIN)) {
            throw new ChatRoomAccessDeniedException("Access denied");
        }
        CounselingState state = stateStore.load(roomId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", config.orchestrated() ? "orchestrated" : "workflow");
        out.put("lastProcessedMessageId", state.getLastProcessedMessageId());
        out.put("memory", CounselingStateStore.JSON.convertValue(CounselingStateStore.snapshot(state), Object.class));

        List<Map<String, Object>> intents = new ArrayList<>();
        for (ClientIntent intent : intentRepository.findAllByChatRoomIdOrderByIdAsc(roomId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "i" + intent.getId());
            row.put("partner", intent.getParticipantRole());
            row.put("kind", intent.getKind());
            row.put("status", intent.getStatus());
            row.put("epistemic", intent.getEpistemic());
            row.put("confidence", intent.getConfidence());
            row.put("importance", intent.getImportance());
            row.put("quote", intent.getQuote());
            row.put("interpretation", intent.getInterpretation());
            row.put("parent", intent.getParentId() == null ? null : "i" + intent.getParentId());
            row.put("change", intent.getChangeKind());
            row.put("alternatives", intent.getAlternativeIds());
            row.put("reason", intent.getStatusReason());
            row.put("changedBy", intent.getChangedBy());
            row.put("session", intent.getSessionNumber());
            row.put("createdAt", intent.getCreatedAt());
            intents.add(row);
        }
        out.put("intents", intents);

        List<Map<String, Object>> questions = new ArrayList<>();
        List<ClarifyingQuestion> all = questionRepository.findAllByChatRoomIdOrderByIdAsc(roomId);
        for (ClarifyingQuestion question : all.subList(Math.max(0, all.size() - 20), all.size())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "q" + question.getId());
            row.put("partner", question.getParticipantRole());
            row.put("type", question.getQuestionType());
            row.put("status", question.getStatus());
            row.put("text", question.getQuestionText());
            row.put("options", QuestionOptions.read(question.getOptionsJson()));
            row.put("readings", question.getTargetIntentIds());
            row.put("reason", question.getReason());
            row.put("impact", question.getDecisionImpact());
            row.put("selected", question.getSelectedOptionId());
            row.put("freeText", question.getFreeText());
            row.put("resolution", question.getResolutionJson());
            row.put("statusReason", question.getStatusReason());
            questions.add(row);
        }
        out.put("questions", questions);

        List<Map<String, Object>> decisions = new ArrayList<>();
        for (DirectorDecisionRecord record : decisionRepository.findByChatRoomIdOrderByIdDesc(roomId, PageRequest.of(0, 15))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "d" + record.getId());
            row.put("kind", record.getKind());
            row.put("status", record.getStatus());
            row.put("action", record.getAction());
            row.put("addressee", record.getAddressee());
            row.put("messages", record.getNewMessageIds());
            row.put("cards", record.getRetrievedCards());
            row.put("rulesUsed", record.getRulesUsed());
            row.put("safety", record.getSafetyLevel());
            row.put("notes", record.getValidationNotes());
            row.put("outcome", record.getOutcomeEffect());
            row.put("outcomeNote", record.getOutcomeNote());
            row.put("directorMs", record.getDirectorMs());
            row.put("voiceMs", record.getVoiceMs());
            row.put("error", record.getError());
            row.put("createdAt", record.getCreatedAt());
            JsonNode accepted = read(record.getAcceptedJson());
            if (accepted != null) {
                row.put("purpose", accepted.path("purpose").asText(""));
                row.put("rationale", accepted.path("rationale").asText(""));
                row.put("summary", accepted.path("summary").asText(""));
                row.put("briefDo", accepted.path("briefDo").asText(""));
                // plain maps and lists: the web layer serializes with its own JSON library
                row.put("reviewIf", plain(accepted.path("reviewIf")));
                row.put("focus", plain(accepted.path("focus")));
                row.put("alternatives", plain(accepted.path("alternatives")));
                row.put("notesFromValidator", plain(accepted.path("notes")));
            }
            decisions.add(row);
        }
        out.put("decisions", decisions);
        return out;
    }

    private static Object plain(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return CounselingStateStore.JSON.convertValue(node, Object.class);
    }

    private static JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return CounselingStateStore.JSON.readTree(json);
        } catch (Exception exception) {
            return null;
        }
    }
}
