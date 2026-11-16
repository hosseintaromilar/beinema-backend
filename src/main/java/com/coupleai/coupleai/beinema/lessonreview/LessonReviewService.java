package com.coupleai.coupleai.beinema.lessonreview;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.counseling.LessonOverrides;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The lesson library for the admin panel: every lesson with its review, and saving a review or an edit. */
@Service
@RequiredArgsConstructor
public class LessonReviewService {

    private static final Logger log = LoggerFactory.getLogger(LessonReviewService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final Set<String> STATUSES = Set.of(LessonReview.PENDING, LessonReview.APPROVED,
            LessonReview.NEEDS_REVISION, LessonReview.REJECTED);

    private final LessonReviewRepository reviews;

    /** The edits saved in the panel are used from the start. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadEdits() {
        Map<String, JsonNode> edits = new LinkedHashMap<>();
        for (LessonReview review : reviews.findAll()) {
            if (review.getEditedJson() == null) {
                continue;
            }
            try {
                edits.put(review.getLessonId(), JSON.readTree(review.getEditedJson()));
            } catch (Exception unreadable) {
                log.warn("Edited lesson {} is unreadable and is not used: {}", review.getLessonId(), unreadable.getMessage());
            }
        }
        LessonOverrides.replaceAll(edits);
        log.info("Lesson edits in use: {}", edits.size());
    }

    /** original and current are plain maps (the web layer writes them as JSON objects). */
    public record Item(String id, String library, String title, String status, String note, boolean edited,
                       Object original, Object current, Long updatedAt) {
    }

    @Transactional(readOnly = true)
    public List<Item> all() {
        Map<String, LessonReview> byId = new LinkedHashMap<>();
        reviews.findAll().forEach(review -> byId.put(key(review.getLessonId()), review));
        List<Item> out = new ArrayList<>();
        for (LessonOverrides.Original original : LessonOverrides.originals()) {
            out.add(item(original, byId.get(key(original.id()))));
        }
        return out;
    }

    /**
     * Saves the status, the note and, when given, the whole edited lesson. The edit keeps the lesson's id
     * and must leave the library readable; content null keeps the current text, reset goes back to the file.
     */
    @Transactional
    public Item save(String lessonId, String status, String note, Map<String, Object> edited, boolean reset, User admin) {
        JsonNode content = edited == null ? null : JSON.valueToTree(edited);
        LessonOverrides.Original original = LessonOverrides.originals().stream()
                .filter(item -> key(item.id()).equals(key(lessonId)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("این درس پیدا نشد."));
        LessonReview review = reviews.findByLessonId(original.id()).orElseGet(() -> {
            LessonReview fresh = new LessonReview();
            fresh.setLessonId(original.id());
            return fresh;
        });
        if (status != null) {
            String wanted = status.trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(wanted)) {
                throw new IllegalArgumentException("وضعیت درس درست نیست.");
            }
            review.setStatus(wanted);
        }
        if (note != null) {
            review.setNote(note.isBlank() ? null : note.trim());
        }
        if (reset) {
            LessonOverrides.set(original.id(), null);
            review.setEditedJson(null);
        } else if (content != null) {
            if (!content.isObject() || !key(content.path("id").asText("")).equals(key(original.id()))) {
                throw new IllegalArgumentException("شناسهٔ درس (id) نباید عوض شود.");
            }
            if (content.equals(original.node())) {
                LessonOverrides.set(original.id(), null);
                review.setEditedJson(null);
            } else {
                LessonOverrides.set(original.id(), content);
                review.setEditedJson(content.toString());
            }
        }
        review.setUpdatedAt(Instant.now());
        review.setUpdatedBy(admin == null ? null : admin.getId());
        return item(original, reviews.save(review));
    }

    private static Item item(LessonOverrides.Original original, LessonReview review) {
        JsonNode current = original.node();
        if (review != null && review.getEditedJson() != null) {
            try {
                current = JSON.readTree(review.getEditedJson());
            } catch (Exception unreadable) {
                current = original.node();
            }
        }
        String title = current.path("fa_title").asText(current.path("title").asText(original.id()));
        return new Item(original.id(), original.library(), title,
                review == null ? LessonReview.PENDING : review.getStatus(),
                review == null ? null : review.getNote(),
                review != null && review.getEditedJson() != null,
                JSON.convertValue(original.node(), Object.class), JSON.convertValue(current, Object.class),
                review == null || review.getUpdatedAt() == null ? null : review.getUpdatedAt().toEpochMilli());
    }

    private static String key(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }
}
