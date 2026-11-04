package com.coupleai.coupleai.beinema.mail;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The admin's emails to users, and the link in group emails that stops them. */
@RestController
@RequiredArgsConstructor
public class AdminEmailController {

    private final AdminEmailService adminEmailService;
    private final CurrentUserService currentUserService;

    @PostMapping("/api/admin/emails/preview")
    public Map<String, Integer> preview(@RequestBody AdminEmailService.Draft draft) {
        return adminEmailService.preview(draft);
    }

    @PostMapping("/api/admin/emails")
    public Map<String, Object> send(@RequestBody AdminEmailService.Draft draft) {
        EmailCampaign campaign = adminEmailService.send(currentUserService.getCurrentUser(), draft);
        return Map.of("id", campaign.getId(), "recipients", campaign.getRecipients());
    }

    @GetMapping("/api/admin/emails")
    public List<Map<String, Object>> history() {
        return adminEmailService.history();
    }

    /** Opened from the email (GET) or by the mail program's own button (POST, one click). */
    @RequestMapping(value = "/api/email/unsubscribe", method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String unsubscribe(@RequestParam("u") Long userId, @RequestParam("t") String token) {
        boolean done = adminEmailService.unsubscribe(userId, token);
        String text = done
                ? "از این به بعد ایمیل گروهی (خبرها و جشنواره‌ها) برایت فرستاده نمی‌شود. کد ورود و ایمیل‌های حسابت همچنان می‌رسند."
                : "این پیوند درست نیست یا کامل باز نشده.";
        return MailLetter.html(text, null);
    }
}
