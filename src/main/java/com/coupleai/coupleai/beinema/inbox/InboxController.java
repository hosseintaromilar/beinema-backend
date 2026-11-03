package com.coupleai.coupleai.beinema.inbox;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The person's inbox (/api/inbox) and the admin's sending (/api/admin/inbox, admins only by the security rules). */
@RestController
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/inbox")
    public List<InboxService.Item> list(@RequestParam(defaultValue = "all") String status) {
        return inboxService.mine(currentUserService.getCurrentUser().getId(), status);
    }

    @GetMapping("/api/inbox/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", inboxService.unread(currentUserService.getCurrentUser().getId()));
    }

    @PostMapping("/api/inbox/{id}/read")
    public void read(@PathVariable Long id) {
        inboxService.read(currentUserService.getCurrentUser().getId(), id);
    }

    @PostMapping("/api/inbox/read-all")
    public void readAll() {
        inboxService.readAll(currentUserService.getCurrentUser().getId());
    }

    @PostMapping("/api/inbox/{id}/archive")
    public void archive(@PathVariable Long id) {
        inboxService.archive(currentUserService.getCurrentUser().getId(), id);
    }

    @DeleteMapping("/api/inbox/{id}")
    public void delete(@PathVariable Long id) {
        inboxService.delete(currentUserService.getCurrentUser().getId(), id);
    }

    @PostMapping("/api/admin/inbox/preview-audience")
    public Map<String, Integer> preview(@RequestBody InboxService.Draft draft) {
        return Map.of("count", inboxService.audienceSize(draft));
    }

    @PostMapping("/api/admin/inbox/messages")
    public Map<String, Object> send(@RequestBody InboxService.Draft draft, HttpServletRequest request) {
        InboxMessage message = inboxService.create(currentUserService.getCurrentUser(), draft, request.getRemoteAddr());
        return Map.of("id", message.getId(), "status", message.getStatus(),
                "recipients", message.getRecipientCount() == null ? 0 : message.getRecipientCount());
    }

    @GetMapping("/api/admin/inbox/messages")
    public List<Map<String, Object>> history() {
        return inboxService.history();
    }

    @PostMapping("/api/admin/inbox/messages/{id}/cancel")
    public void cancel(@PathVariable Long id, HttpServletRequest request) {
        inboxService.cancel(currentUserService.getCurrentUser(), id, request.getRemoteAddr());
    }

    @PostMapping("/api/admin/inbox/messages/{id}/revoke")
    public void revoke(@PathVariable Long id, HttpServletRequest request) {
        inboxService.revoke(currentUserService.getCurrentUser(), id, request.getRemoteAddr());
    }

    @GetMapping("/api/admin/inbox/audit-log")
    public List<InboxAudit> audit() {
        return inboxService.auditLog();
    }
}
