package com.coupleai.coupleai.beinema.lessonreview;

import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The lesson library in the admin panel: review, note and edit each lesson. */
@RestController
@RequiredArgsConstructor
public class LessonReviewController {

    private final LessonReviewService lessons;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/admin/lessons")
    public List<LessonReviewService.Item> all() {
        return lessons.all();
    }

    /** Body: status, note, content (the whole lesson) and reset (back to the file's text); each optional. */
    @PutMapping("/api/admin/lessons/{id}")
    @SuppressWarnings("unchecked")
    public LessonReviewService.Item save(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object content = body.get("content");
        if (content != null && !(content instanceof Map)) {
            throw new IllegalArgumentException("متن درس درست فرستاده نشده است.");
        }
        Object note = body.get("note");
        return lessons.save(id,
                body.get("status") == null ? null : String.valueOf(body.get("status")),
                body.containsKey("note") ? (note == null ? "" : String.valueOf(note)) : null,
                (Map<String, Object>) content,
                Boolean.TRUE.equals(body.get("reset")),
                currentUserService.getCurrentUser());
    }
}
