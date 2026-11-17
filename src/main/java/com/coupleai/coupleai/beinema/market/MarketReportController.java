package com.coupleai.coupleai.beinema.market;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** «گزارش‌های مالی»: the market reports a person can open, a fresh one, and the reference figures in the admin panel. */
@RestController
@RequiredArgsConstructor
public class MarketReportController {

    private final MarketReportService reports;
    private final FinanceReferenceService reference;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/market-reports")
    public Map<String, Object> list() {
        User user = currentUserService.getCurrentUser();
        boolean available = reports.available(user);
        return Map.of("reports", available ? reports.list(user) : List.of(), "available", available);
    }

    /** Whether the tab «گزارش‌های مالی» is shown: Pouria gave this person a report in a session. */
    @GetMapping("/api/market-reports/available")
    public Map<String, Object> available() {
        return Map.of("available", reports.available(currentUserService.getCurrentUser()));
    }

    @GetMapping("/api/market-reports/{publicId}")
    public Map<String, Object> one(@PathVariable String publicId) {
        return reports.view(publicId, currentUserService.getCurrentUser());
    }

    @PostMapping("/api/market-reports")
    public Map<String, Object> create() {
        return reports.createFor(currentUserService.getCurrentUser());
    }

    @GetMapping("/api/admin/finance-reference")
    public Map<String, Object> referenceNow() {
        return reference.view();
    }

    @PutMapping("/api/admin/finance-reference")
    public Map<String, Object> saveReference(@RequestBody Map<String, Object> body) {
        User admin = currentUserService.getCurrentUser();
        return reference.save(body, admin.getEmail() == null ? String.valueOf(admin.getId()) : admin.getEmail());
    }

    @DeleteMapping("/api/admin/finance-reference")
    public Map<String, Object> resetReference() {
        return reference.reset();
    }
}
