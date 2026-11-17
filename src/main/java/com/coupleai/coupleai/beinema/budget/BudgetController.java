package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.counseling.FinanceService;
import com.coupleai.coupleai.beinema.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The finance tab of the dashboard: the household's conversations with Pouria, its profile and its budget book. */
@RestController
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budget;
    private final FinanceService finance;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/finance/rooms")
    public Map<String, Object> rooms() {
        List<Map<String, Object>> rooms = budget.rooms(currentUserService.getCurrentUser());
        return Map.of("rooms", rooms);
    }

    /** The profile: the risk results, the picture, the effective risk level, the plan, and the book's month and averages. */
    @GetMapping("/api/finance/rooms/{roomId}/profile")
    public Map<String, Object> profile(@PathVariable Long roomId) {
        User user = currentUserService.getCurrentUser();
        budget.requireMember(roomId, user);
        Map<String, Object> out = new LinkedHashMap<>(finance.profile(roomId, user));
        out.put("budget", budget.month(roomId, null, user, null));
        return out;
    }

    @GetMapping("/api/finance/rooms/{roomId}/budget")
    public Map<String, Object> month(@PathVariable Long roomId, @RequestParam(required = false) String month) {
        return budget.month(roomId, month, currentUserService.getCurrentUser(), null);
    }

    /** Body: category, amount (tomans), daysAgo (0 today), note. */
    @PostMapping("/api/finance/rooms/{roomId}/budget")
    public Map<String, Object> add(@PathVariable Long roomId, @RequestBody Map<String, Object> body) {
        long amount;
        try {
            amount = Math.round(Double.parseDouble(String.valueOf(body.get("amount")).replace(",", "")));
        } catch (NumberFormatException wrong) {
            throw new IllegalArgumentException("مبلغ را به تومان بنویس.");
        }
        int daysAgo = body.get("daysAgo") instanceof Number number ? number.intValue() : 0;
        LocalDate day = LocalDate.now(MarketDataService.TEHRAN).minusDays(Math.max(0, daysAgo));
        Object note = body.get("note");
        return budget.add(roomId, currentUserService.getCurrentUser(), String.valueOf(body.get("category")), amount, day,
                note == null ? null : String.valueOf(note));
    }

    @DeleteMapping("/api/finance/rooms/{roomId}/budget/{entryId}")
    public Map<String, Object> delete(@PathVariable Long roomId, @PathVariable String entryId) {
        budget.delete(roomId, entryId, currentUserService.getCurrentUser());
        return Map.of("status", "OK");
    }

    /** Body: {CATEGORY: tomans a month}; 0 removes a limit. */
    @PutMapping("/api/finance/rooms/{roomId}/budget/limits")
    public Map<String, Object> limits(@PathVariable Long roomId, @RequestBody Map<String, Object> body) {
        Map<String, Long> amounts = new LinkedHashMap<>();
        body.forEach((category, value) -> {
            try {
                amounts.put(category, Math.round(Double.parseDouble(String.valueOf(value).replace(",", ""))));
            } catch (NumberFormatException ignored) {
                // a value that is not a number is left as it was
            }
        });
        User user = currentUserService.getCurrentUser();
        budget.setLimits(roomId, user, amounts);
        return budget.month(roomId, null, user, null);
    }
}
