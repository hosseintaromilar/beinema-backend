package com.coupleai.coupleai.beinema.admin;

import com.coupleai.coupleai.beinema.admin.AdminAccountService;
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

/** Admins and the access log are the supervisor's (see SecurityConfig); accounts are for every admin. */
@RestController
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService service;

    @GetMapping("/api/admin/admins")
    public List<Map<String, Object>> admins() {
        return service.admins();
    }

    @PostMapping("/api/admin/admins")
    public Map<String, Object> createAdmin(@RequestBody Map<String, String> body) {
        return service.createAdmin(body.get("username"), body.get("firstName"), body.get("lastName"), body.get("password"));
    }

    @DeleteMapping("/api/admin/admins/{id}")
    public void removeAdmin(@PathVariable Long id) {
        service.removeAdmin(id);
    }

    @GetMapping("/api/admin/access-log")
    public List<Map<String, Object>> accessLog() {
        return service.accessLog();
    }

    @GetMapping("/api/admin/users")
    public List<Map<String, Object>> users(@RequestParam(required = false) String q) {
        return service.users(q);
    }

    @GetMapping("/api/admin/users/{id}")
    public Map<String, Object> account(@PathVariable Long id) {
        return service.account(id);
    }

    @GetMapping("/api/admin/users/{id}/rooms/{roomId}")
    public Map<String, Object> chat(@PathVariable Long id, @PathVariable Long roomId) {
        return service.chat(id, roomId);
    }
}
