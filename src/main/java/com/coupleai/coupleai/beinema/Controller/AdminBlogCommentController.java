package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Enum.BlogCommentStatus;
import com.coupleai.coupleai.beinema.Service.BlogCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The admin's review of readers' comments (admins only, like every /api/admin path). */
@RestController
@RequestMapping("/api/admin/blog-comments")
@RequiredArgsConstructor
public class AdminBlogCommentController {

    private final BlogCommentService commentService;

    /** status = PENDING, APPROVED or REJECTED; empty for the latest of all. */
    @GetMapping
    public List<BlogCommentService.AdminComment> list(@RequestParam(required = false) String status) {
        return commentService.forAdmin(status);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        commentService.review(id, BlogCommentStatus.APPROVED);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id) {
        commentService.review(id, BlogCommentStatus.REJECTED);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
