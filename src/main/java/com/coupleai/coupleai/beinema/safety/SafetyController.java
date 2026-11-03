package com.coupleai.coupleai.beinema.safety;

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
import java.util.Set;

/** The help card for the chat's menu, and the admin's phrase list. */
@RestController
@RequiredArgsConstructor
public class SafetyController {

    private static final Set<String> CATEGORIES = Set.of(SafetyRule.SEXTORTION, SafetyRule.VIOLENCE,
            SafetyRule.SELF_HARM, SafetyRule.CHILD_ABUSE);

    private final SafetyRuleRepository ruleRepository;
    private final com.coupleai.coupleai.beinema.Repository.MessageAttachmentRepository attachmentRepository;
    private final com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository participantRepository;
    private final com.coupleai.coupleai.beinema.Repository.UserRepository userRepository;
    private final com.coupleai.coupleai.beinema.ai.AdminAlertRepository alertRepository;
    private final com.coupleai.coupleai.beinema.Repository.ChatRoomRepository chatRoomRepository;

    /** A person reports a picture in their room; the admin sees which room and file, never the chat. */
    @PostMapping("/api/chat-rooms/{roomId}/attachments/{attachmentId}/report")
    public Map<String, String> report(@PathVariable Long roomId, @PathVariable Long attachmentId,
                                      org.springframework.security.core.Authentication authentication) {
        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("حساب پیدا نشد."));
        attachmentRepository.findById(attachmentId)
                .filter(item -> roomId.equals(item.getChatRoomId()))
                .orElseThrow(() -> new IllegalArgumentException("این فایل پیدا نشد."));
        boolean member = chatRoomRepository.findById(roomId)
                .map(room -> participantRepository.findAllByChatRoom(room).stream()
                        .anyMatch(person -> person.getUser() != null && person.getUser().getId().equals(user.getId())))
                .orElse(false);
        if (!member) {
            throw new IllegalArgumentException("این فایل پیدا نشد.");
        }
        var alert = new com.coupleai.coupleai.beinema.ai.AdminAlert();
        alert.setKind(com.coupleai.coupleai.beinema.ai.AdminAlert.IMAGE_REPORT);
        alert.setChatRoomId(roomId);
        alert.setDetail("گزارش تصویر: فایل " + attachmentId + " در گفتگوی " + roomId + " (کاربر " + user.getId() + ")");
        alertRepository.save(alert);
        return Map.of("status", "REPORTED");
    }

    @GetMapping("/api/safety/card")
    public Map<String, String> card() {
        return Map.of("text", SafetyCardService.CARD);
    }

    @GetMapping("/api/admin/safety-rules")
    public List<SafetyRule> rules() {
        return ruleRepository.findAllByOrderByCategoryAscIdAsc();
    }

    @PostMapping("/api/admin/safety-rules")
    public SafetyRule add(@RequestBody Map<String, Object> body) {
        return ruleRepository.save(fill(new SafetyRule(), body));
    }

    @PutMapping("/api/admin/safety-rules/{id}")
    public SafetyRule change(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SafetyRule rule = ruleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("این قاعده پیدا نشد."));
        return ruleRepository.save(fill(rule, body));
    }

    @DeleteMapping("/api/admin/safety-rules/{id}")
    public void remove(@PathVariable Long id) {
        ruleRepository.deleteById(id);
    }

    private static SafetyRule fill(SafetyRule rule, Map<String, Object> body) {
        String category = String.valueOf(body.getOrDefault("category", rule.getCategory()));
        String pattern = String.valueOf(body.getOrDefault("pattern", rule.getPattern() == null ? "" : rule.getPattern())).trim();
        String audience = String.valueOf(body.getOrDefault("audience", rule.getAudience()));
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("دستهٔ نامعتبر.");
        }
        if (pattern.length() < 2 || pattern.length() > 120) {
            throw new IllegalArgumentException("عبارت باید بین ۲ تا ۱۲۰ نویسه باشد.");
        }
        rule.setCategory(category);
        rule.setPattern(pattern);
        rule.setAudience(SafetyRule.OTHERS.equals(audience) ? SafetyRule.OTHERS : SafetyRule.SELF);
        rule.setLang(String.valueOf(body.getOrDefault("lang", rule.getLang())));
        if (body.containsKey("enabled")) {
            rule.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        return rule;
    }
}
