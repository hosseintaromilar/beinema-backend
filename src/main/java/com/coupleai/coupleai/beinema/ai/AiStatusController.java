package com.coupleai.coupleai.beinema.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The service's state for every page (no cause is told), and the admin's view of outages and alerts. */
@RestController
@RequiredArgsConstructor
public class AiStatusController {

    private final AiHealth aiHealth;
    private final AiIncidentRepository incidentRepository;
    private final AdminAlertRepository alertRepository;
    private final com.coupleai.coupleai.beinema.Repository.DeletionLogRepository deletionLogRepository;

    /** Deletions for the admin: ids and outcomes only. */
    @GetMapping("/api/admin/deletions")
    public List<com.coupleai.coupleai.beinema.Entity.DeletionLog> deletions() {
        return deletionLogRepository.findTop200ByOrderByIdDesc();
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        AiHealth.Status status = aiHealth.status();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ai", status.ai());
        body.put("since", status.since());
        body.put("retry_after_sec", status.retryAfterSec());
        return body;
    }

    @GetMapping("/api/admin/ai")
    public Map<String, Object> admin() {
        Map<String, Object> body = new LinkedHashMap<>(status());
        body.put("incidents", incidentRepository.findTop50ByOrderByStartedAtDesc());
        List<AdminAlert> alerts = alertRepository.findTop100ByOrderByIdDesc();
        body.put("alerts", alerts);
        body.put("unseen", alertRepository.countBySeenFalse());
        // Metis has no balance API in its documents; the balance is read in its console
        body.put("credit", null);
        return body;
    }

    /** After charging the account: back to normal at once, without waiting for the test call. */
    @PostMapping("/api/admin/ai/reset")
    public Map<String, Object> reset() {
        aiHealth.reset();
        return status();
    }

    @PostMapping("/api/admin/ai/alerts/seen")
    public void seen() {
        List<AdminAlert> alerts = alertRepository.findAll().stream().filter(alert -> !alert.isSeen()).toList();
        alerts.forEach(alert -> alert.setSeen(true));
        alertRepository.saveAll(alerts);
    }
}
