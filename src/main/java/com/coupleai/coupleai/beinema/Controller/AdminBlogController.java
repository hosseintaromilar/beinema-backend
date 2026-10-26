package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The admin's review of the writer's posts (admins only, like every /api/admin path). */
@RestController
@RequestMapping("/api/admin/blog")
@RequiredArgsConstructor
public class AdminBlogController {

    private final BlogService blogService;
    private final com.coupleai.coupleai.beinema.Service.BlogImageGenerator blogImageGenerator;
    private final com.coupleai.coupleai.beinema.Service.BlogWriter blogWriter;

    @GetMapping
    public List<BlogPost> list() {
        return blogService.all();
    }

    /** Starts writing a new post in the background (about a minute); the panel follows /writer. */
    @PostMapping("/write")
    public com.coupleai.coupleai.beinema.Service.BlogWriter.Status write() {
        return blogWriter.startWriting();
    }

    @GetMapping("/writer")
    public com.coupleai.coupleai.beinema.Service.BlogWriter.Status writer() {
        return blogWriter.status();
    }

    @GetMapping("/{id}")
    public BlogService.PostView view(@PathVariable Long id) {
        return blogService.view(id);
    }

    /** Makes the four pictures of the current version (takes up to a few minutes). */
    @PostMapping("/{id}/images")
    public Map<String, Object> makeImages(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        double cost = blogImageGenerator.generate(id, body == null ? null : body.get("description"));
        return Map.of("cost", cost, "images", blogService.view(id).images());
    }

    @PostMapping("/{id}/image/{imageId}")
    public BlogPost chooseImage(@PathVariable Long id, @PathVariable Long imageId) {
        return blogService.chooseImage(id, imageId);
    }

    /** The admin's edit of the current version. Body: title, slug, summary, bodyMarkdown, tags. */
    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public BlogService.PostView edit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return blogService.edit(id, new BlogService.Draft(body.get("title"), body.get("slug"), body.get("summary"),
                body.get("bodyMarkdown"), body.get("tags"), null, null));
    }

    @PostMapping("/{id}/publish")
    public BlogPost publish(@PathVariable Long id) {
        return blogService.publish(id);
    }

    /** Body: { "note": "what to change" }. */
    @PostMapping("/{id}/revise")
    public BlogPost revise(@PathVariable Long id, @RequestBody Map<String, String> body) {
        BlogPost post = blogService.requestRevision(id, body == null ? null : body.get("note"));
        rewriteInBackground(id);
        return post;
    }

    /** Asks the writer again for a post still waiting for its next version (if the first try failed). */
    @PostMapping("/{id}/rewrite")
    public Map<String, String> rewrite(@PathVariable Long id) {
        rewriteInBackground(id);
        return Map.of("status", "REVISING");
    }

    private void rewriteInBackground(Long id) {
        blogWriter.startRevising(id);
    }

    @PostMapping("/{id}/reject")
    public BlogPost reject(@PathVariable Long id) {
        return blogService.reject(id);
    }
}
