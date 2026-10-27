package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Support tickets: the person's side (/api/support) and support's side (/api/admin/support, admins only). */
@RestController
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @GetMapping("/api/support/tickets")
    public List<SupportService.TicketSummary> mine() {
        return supportService.mine();
    }

    /** Body: subject, category (ACCOUNT, PAYMENT, CHAT, BUG, OTHER), body. */
    @PostMapping("/api/support/tickets")
    public SupportService.TicketView create(@RequestBody Map<String, String> body) {
        return supportService.create(body.get("subject"), body.get("category"), body.get("body"));
    }

    @GetMapping("/api/support/tickets/{id}")
    public SupportService.TicketView open(@PathVariable Long id) {
        return supportService.openMine(id);
    }

    @PostMapping("/api/support/tickets/{id}/messages")
    public SupportService.TicketView reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return supportService.replyMine(id, body.get("body"));
    }

    @PostMapping("/api/support/tickets/{id}/close")
    public SupportService.TicketView close(@PathVariable Long id) {
        return supportService.closeMine(id);
    }

    @GetMapping("/api/support/unread")
    public Map<String, Long> unread() {
        return Map.of("count", supportService.unreadForMe());
    }

    @GetMapping("/api/admin/support/tickets")
    public List<SupportService.TicketSummary> forAdmin(@RequestParam(required = false) String status) {
        return supportService.forAdmin(status);
    }

    @GetMapping("/api/admin/support/tickets/{id}")
    public SupportService.TicketView adminView(@PathVariable Long id) {
        return supportService.adminView(id);
    }

    @PostMapping("/api/admin/support/tickets/{id}/messages")
    public SupportService.TicketView adminReply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return supportService.adminReply(id, body.get("body"));
    }

    @PostMapping("/api/admin/support/tickets/{id}/close")
    public SupportService.TicketView adminClose(@PathVariable Long id) {
        return supportService.adminClose(id);
    }
}
