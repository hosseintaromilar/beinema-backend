package com.coupleai.coupleai.beinema.blog;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.blog.BlogService;
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
    private final com.coupleai.coupleai.beinema.blog.BlogImageGenerator blogImageGenerator;
    private final com.coupleai.coupleai.beinema.blog.BlogWriter blogWriter;
    private final com.coupleai.coupleai.beinema.files.FileStorageService fileStorageService;
    private final com.coupleai.coupleai.beinema.blog.BlogCleanupService blogCleanupService;

    @GetMapping
    public List<BlogPost> list() {
        return blogService.all();
    }

    /**
     * Starts writing a new post in the background (about a minute); the panel follows /writer.
     * Body (optional): { "subject": "the topic in Persian", "slug": "address" }; without a subject
     * the next topic of the plan.
     */
    @PostMapping("/write")
    public com.coupleai.coupleai.beinema.blog.BlogWriter.Status write(@RequestBody(required = false) Map<String, String> body) {
        return blogWriter.startWriting(body == null ? null
                : com.coupleai.coupleai.beinema.blog.BlogWriter.adminTopic(body.get("subject"), body.get("slug")));
    }

    /** A post the admin writes by hand: a draft like the writer's, waiting for a picture and publishing. */
    @PostMapping
    public BlogPost create(@RequestBody Map<String, String> body) {
        String slug = body.get("slug");
        return blogService.createDraft(new BlogService.Draft(body.get("title"), slug, body.get("summary"),
                body.get("bodyMarkdown"), body.get("tags"), null,
                slug == null || slug.isBlank() ? null : BlogService.slugOf(slug, null)));
    }

    @GetMapping("/writer")
    public com.coupleai.coupleai.beinema.blog.BlogWriter.Status writer() {
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

    /** The admin's own picture for the post (jpg, png or webp, up to 5 MB); it becomes the chosen one. */
    @PostMapping(value = "/{id}/image-upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public BlogService.PostView uploadImage(@PathVariable Long id,
                                            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        blogService.addUploadedImage(id, fileStorageService.storeBlogImage(file));
        return blogService.view(id);
    }

    /** Removes a rejected post with its versions, pictures and comments. */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        blogCleanupService.deletePost(id);
        return Map.of("status", "DELETED");
    }

    /** Removes one picture option (not the chosen one). */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/image/{imageId}")
    public BlogService.PostView deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        blogCleanupService.deleteImage(id, imageId);
        return blogService.view(id);
    }

    @PostMapping("/{id}/image/{imageId}")
    public BlogPost chooseImage(@PathVariable Long id, @PathVariable Long imageId) {
        return blogService.chooseImage(id, imageId);
    }

    /** The admin's edit of the current version. Body: title, slug, summary, bodyMarkdown, tags. */
    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public BlogService.PostView edit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        BlogService.PostView before = blogService.view(id);
        java.util.Set<String> pictures = com.coupleai.coupleai.beinema.blog.BlogCleanupService.picturesIn(
                blogService.currentVersion(before.post()).getBodyMarkdown());
        BlogService.PostView after = blogService.edit(id, new BlogService.Draft(body.get("title"), body.get("slug"),
                body.get("summary"), body.get("bodyMarkdown"), body.get("tags"), null, null));
        // a picture taken out of the text no longer takes room on the server
        blogCleanupService.dropUnusedPictures(pictures, body.get("bodyMarkdown"));
        return after;
    }

    /** A picture for inside the text (jpg, png, webp, up to 5 MB); the editor puts its line where the cursor is. */
    @PostMapping(value = "/{id}/inline-image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> inlineImage(@PathVariable Long id,
                                           @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        blogService.view(id);
        return Map.of("path", fileStorageService.storeBlogImage(file));
    }

    /** Body: { "slugs": ["…"] }: the posts suggested at the end of this one (published, at most four). */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/related")
    public BlogPost related(@PathVariable Long id, @RequestBody Map<String, List<String>> body) {
        return blogService.setRelated(id, body == null ? null : body.get("slugs"));
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
