package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Enum.IntentEpistemic;
import com.coupleai.coupleai.beinema.Enum.IntentKind;
import com.coupleai.coupleai.beinema.Enum.IntentStatus;
import com.coupleai.coupleai.beinema.Repository.ClientIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the wants and readings of each partner (client_intents). A partner's
 * explicit words are stored as STATED; our readings stay UNCONFIRMED until the
 * partner confirms them. A change never deletes a row: the new row points to
 * its parent, which becomes REFINED (elaboration) or SUPERSEDED (true change).
 * Callers hold the transaction.
 */
@Service
@RequiredArgsConstructor
public class IntentLedger {

    private final ClientIntentRepository repository;

    public record Resolution(List<Long> confirmed, List<Long> rejected, String note) {
    }

    /** Applies validated operations of one decision; returns each new ref (n1) with its id. */
    public Map<String, Long> apply(Long roomId, Map<String, Long> userIdByRole, List<DirectorDecision.IntentOp> ops,
                                   Long decisionId, int session) {
        Map<String, Long> refs = new LinkedHashMap<>();
        Map<String, ClientIntent> created = new HashMap<>();
        String by = "d" + decisionId;

        for (DirectorDecision.IntentOp op : ops) {
            if (!"CREATE".equals(op.op)) {
                continue;
            }
            Long userId = userIdByRole.get(op.participant);
            if (userId == null) {
                continue;
            }
            ClientIntent intent = new ClientIntent();
            intent.setChatRoomId(roomId);
            intent.setParticipantUserId(userId);
            intent.setParticipantRole(op.participant);
            intent.setKind(IntentKind.valueOf(op.kind));
            intent.setQuote(op.quote);
            intent.setInterpretation(op.interpretation);
            intent.setEpistemic(IntentEpistemic.valueOf(op.epistemic));
            intent.setStatus("STATED".equals(op.epistemic) ? IntentStatus.CONFIRMED : IntentStatus.UNCONFIRMED);
            intent.setConfidence(op.confidence);
            intent.setImportance(op.importance);
            intent.setEvidenceMessageIds(csv(op.evidence));
            intent.setParentId(op.parent);
            intent.setChangeKind(op.parent == null ? null : op.change);
            intent.setStatusReason("STATED".equals(op.epistemic) ? "said by the partner" : "reading of the director");
            intent.setChangedBy(by);
            intent.setSessionNumber(session);
            intent.setCreatedDecisionId(decisionId);
            repository.save(intent);
            refs.put(op.ref, intent.getId());
            created.put(op.ref, intent);
        }

        for (DirectorDecision.IntentOp op : ops) {
            switch (op.op) {
                case "CREATE" -> {
                    ClientIntent intent = created.get(op.ref);
                    if (intent == null) {
                        continue;
                    }
                    List<Long> alternatives = new ArrayList<>();
                    for (String alternative : op.alternatives) {
                        Long id = resolve(alternative, refs);
                        if (id != null && !id.equals(intent.getId())) {
                            alternatives.add(id);
                        }
                    }
                    intent.setAlternativeIds(csv(alternatives));
                    repository.save(intent);
                    if (op.parent != null) {
                        repository.findById(op.parent).ifPresent(parent -> closeParent(parent, intent.getId(), op.change, by));
                    }
                }
                case "STATUS" -> repository.findById(op.id).ifPresent(intent -> {
                    intent.setStatus(IntentStatus.valueOf(op.status));
                    if ("CONFIRMED".equals(op.status) && intent.getEpistemic() != IntentEpistemic.STATED) {
                        intent.setEpistemic(IntentEpistemic.CONFIRMED_BY_CLIENT);
                    }
                    intent.setStatusReason(op.reason);
                    intent.setChangedBy(by);
                    List<Long> evidence = new ArrayList<>(parse(intent.getEvidenceMessageIds()));
                    op.evidence.stream().filter(id -> !evidence.contains(id)).forEach(evidence::add);
                    intent.setEvidenceMessageIds(csv(evidence));
                    repository.save(intent);
                });
                case "SUPERSEDE" -> {
                    Long replacement = resolve(op.by, refs);
                    if (replacement == null) {
                        continue;
                    }
                    repository.findById(op.id).ifPresent(intent -> closeParent(intent, replacement, op.change, by));
                    repository.findById(replacement).ifPresent(next -> {
                        if (next.getParentId() == null && !next.getId().equals(op.id)) {
                            next.setParentId(op.id);
                            next.setChangeKind(op.change);
                            repository.save(next);
                        }
                    });
                }
                case "PRIORITY" -> repository.findById(op.id).ifPresent(intent -> {
                    intent.setImportance(op.importance);
                    intent.setStatusReason("PRIORITY_SHIFT: " + op.reason);
                    intent.setChangedBy(by);
                    repository.save(intent);
                });
                default -> {
                }
            }
        }
        return refs;
    }

    /** What an answer to a structured question means for its readings; only readings still open change. */
    public Resolution resolveFromAnswer(ClarifyingQuestion question, QuestionOptions.Option chosen) {
        List<Long> targets = parse(question.getTargetIntentIds());
        List<Long> confirmed = new ArrayList<>();
        List<Long> rejected = new ArrayList<>();
        String by = "q" + question.getId();
        String note;
        switch (chosen.kind()) {
            case QuestionOptions.HYPOTHESIS, QuestionOptions.YES -> {
                for (Long target : targets) {
                    boolean picked = target.equals(chosen.intentId());
                    if (update(target, picked ? IntentStatus.CONFIRMED : IntentStatus.REJECTED,
                            picked ? "chosen by the partner in " + by : "not chosen by the partner in " + by, by)) {
                        (picked ? confirmed : rejected).add(target);
                    }
                }
                note = "the partner confirmed a reading";
            }
            case QuestionOptions.NONE, QuestionOptions.NO -> {
                for (Long target : targets) {
                    if (update(target, IntentStatus.REJECTED, "the partner said none of these fit (" + by + ")", by)) {
                        rejected.add(target);
                    }
                }
                note = "the partner rejected the readings";
            }
            default -> note = "the partner is not sure yet; readings stay unconfirmed";
        }
        return new Resolution(confirmed, rejected, note);
    }

    private boolean update(Long id, IntentStatus status, String reason, String by) {
        ClientIntent intent = repository.findById(id).orElse(null);
        if (intent == null || intent.getStatus() != IntentStatus.UNCONFIRMED) {
            return false;
        }
        intent.setStatus(status);
        if (status == IntentStatus.CONFIRMED) {
            intent.setEpistemic(IntentEpistemic.CONFIRMED_BY_CLIENT);
        }
        intent.setStatusReason(reason);
        intent.setChangedBy(by);
        repository.save(intent);
        return true;
    }

    private void closeParent(ClientIntent parent, Long childId, String change, String by) {
        boolean elaboration = !"TRUE_CHANGE".equals(change);
        parent.setStatus(elaboration ? IntentStatus.REFINED : IntentStatus.SUPERSEDED);
        parent.setStatusReason((elaboration ? "made more precise by i" : "replaced by i") + childId);
        parent.setChangedBy(by);
        repository.save(parent);
    }

    private static Long resolve(String reference, Map<String, Long> refs) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String value = reference.trim();
        if (refs.containsKey(value)) {
            return refs.get(value);
        }
        return DecisionValidator.id(value);
    }

    static String csv(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (Long id : ids) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(id);
        }
        return out.length() > 250 ? out.substring(0, out.lastIndexOf(",", 250)) : out.toString();
    }

    static List<Long> parse(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return ids;
    }
}
