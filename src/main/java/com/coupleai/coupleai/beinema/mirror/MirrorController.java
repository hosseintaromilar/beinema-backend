package com.coupleai.coupleai.beinema.mirror;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.mail.MailLetter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The nightly mirrors: a conversation's own, the dashboard card, and the email's «stop» link. */
@RestController
@RequiredArgsConstructor
public class MirrorController {

    private final MirrorReportService mirrors;
    private final CurrentUserService currentUserService;
    private final com.coupleai.coupleai.beinema.lock.RoomLockService roomLocks;

    @GetMapping("/api/chat-rooms/{chatRoomId}/mirrors")
    public List<MirrorReportService.View> history(@PathVariable Long chatRoomId) {
        return mirrors.history(chatRoomId, currentUserService.getCurrentUser());
    }

    @GetMapping("/api/mirrors/recent")
    public List<MirrorReportService.View> recent() {
        // a locked conversation shows nothing of itself outside its door
        var me = currentUserService.getCurrentUser();
        List<MirrorReportService.View> recent = mirrors.recent(me);
        var locked = roomLocks.lockedRooms(me.getId(), recent.stream().map(MirrorReportService.View::chatRoomId).toList());
        return recent.stream().filter(view -> !locked.contains(view.chatRoomId())).toList();
    }

    /** The mirrors this person has not opened yet, newest of each conversation (the dashboard cards). */
    @GetMapping("/api/mirrors/unopened")
    public List<MirrorReportService.View> unopened() {
        var me = currentUserService.getCurrentUser();
        List<MirrorReportService.View> waiting = mirrors.unopened(me);
        var locked = roomLocks.lockedRooms(me.getId(), waiting.stream().map(MirrorReportService.View::chatRoomId).toList());
        return waiting.stream().filter(view -> !locked.contains(view.chatRoomId())).toList();
    }

    /** One mirror (the link in the email); a locked conversation shows nothing of itself outside its door. */
    @GetMapping("/api/mirrors/{id}")
    public MirrorReportService.View one(@org.springframework.web.bind.annotation.PathVariable Long id) {
        var me = currentUserService.getCurrentUser();
        MirrorReportService.View view = mirrors.one(id, me);
        if (roomLocks.lockedRooms(me.getId(), List.of(view.chatRoomId())).contains(view.chatRoomId())) {
            throw new IllegalArgumentException("این گفتگو قفل است؛ آینه‌اش را داخل خود گفتگو ببین.");
        }
        return view;
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/mirrors/{id}/opened")
    public void opened(@org.springframework.web.bind.annotation.PathVariable Long id) {
        mirrors.opened(id, currentUserService.getCurrentUser());
    }

    /** An admin sends a sample mirror to their own address, to see it and try its links. */
    @org.springframework.web.bind.annotation.PostMapping("/api/admin/mirrors/sample")
    public java.util.Map<String, String> sample() {
        return java.util.Map.of("sentTo", mirrors.sendSample(currentUserService.getCurrentUser()));
    }

    /** Opened from the email (GET) or by the mail program's own button (POST, one click). */
    @RequestMapping(value = "/api/email/mirror-off", method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String stop(@RequestParam("u") Long userId, @RequestParam("t") String token) {
        boolean done = mirrors.stopEmails(userId, token);
        String text = done
                ? "از این به بعد آینهٔ گفتگوها برایت ایمیل نمی‌شود. آینه همچنان هر شب در خود بین‌ما ساخته می‌شود و هر وقت خواستی، از پروفایل دوباره روشنش کن."
                : "این پیوند درست نیست یا کامل باز نشده.";
        return MailLetter.html(text, null);
    }
}
