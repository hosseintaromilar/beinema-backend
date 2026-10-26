package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Service.BlogCommentService;
import com.coupleai.coupleai.beinema.Service.BlogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The public blog of the site: only published posts. */
@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;
    private final BlogCommentService commentService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String siteUrl;

    @GetMapping
    public List<BlogService.PublicPost> list() {
        return blogService.publishedCards();
    }

    /** For search engines: the blog list and every published post (robots.txt points here). */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public String sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
                .append("  <url><loc>").append(siteUrl).append("/blog</loc></url>\n");
        for (BlogPost post : blogService.published()) {
            xml.append("  <url><loc>").append(siteUrl).append("/blog/").append(post.getSlug()).append("</loc>");
            if (post.getPublishedAt() != null) {
                xml.append("<lastmod>").append(post.getPublishedAt().toString(), 0, 10).append("</lastmod>");
            }
            xml.append("</url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    @GetMapping("/{slug}")
    public BlogService.PublicPost post(@PathVariable String slug) {
        return blogService.publishedBySlug(slug);
    }

    @GetMapping("/{slug}/comments")
    public List<BlogCommentService.PublicComment> comments(@PathVariable String slug) {
        return commentService.approved(slug);
    }

    /** Body: name, body, website (a hidden field people leave empty). The comment waits for the admin. */
    @PostMapping("/{slug}/comments")
    public Map<String, String> comment(@PathVariable String slug,
                                       @RequestBody Map<String, String> body,
                                       HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String address = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        commentService.submit(slug, body.get("name"), body.get("body"), body.get("website"), address);
        return Map.of("status", "PENDING");
    }
}
