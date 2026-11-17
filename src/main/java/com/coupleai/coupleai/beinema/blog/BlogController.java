package com.coupleai.coupleai.beinema.blog;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.blog.BlogCommentService;
import com.coupleai.coupleai.beinema.blog.BlogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** The public blog of the site: only published posts. */
@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;
    private final BlogCommentService commentService;
    private final AgentRepository agentRepository;

    /** The readable address of each companion's page (the frontend's src/content/agentPages.js). */
    static final Map<AgentType, String> AGENT_SLUGS = Map.of(
            AgentType.RELATIONSHIP, "couple-relationship",
            AgentType.FINANCIAL, "couples-finance",
            AgentType.PARENTING, "parenting",
            AgentType.PREMARITAL, "premarital",
            AgentType.IN_LAWS, "in-laws",
            AgentType.SEXOLOGY, "intimacy");

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
                .append("  <url><loc>").append(siteUrl).append("/</loc></url>\n")
                .append("  <url><loc>").append(siteUrl).append("/about</loc></url>\n")
                .append("  <url><loc>").append(siteUrl).append("/agents</loc></url>\n")
                .append("  <url><loc>").append(siteUrl).append("/blog</loc></url>\n")
                .append("  <url><loc>").append(siteUrl).append("/contact</loc></url>\n")
                .append("  <url><loc>").append(siteUrl).append("/privacy</loc></url>\n")
                .append("  <url><loc>").append(siteUrl).append("/terms</loc></url>\n");
        // each active companion's own page
        agentRepository.findByStatus(AgentStatus.ACTIVE).stream()
                .map(agent -> AGENT_SLUGS.get(agent.getType()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(slug -> xml.append("  <url><loc>").append(siteUrl).append("/agents/").append(slug).append("</loc></url>\n"));
        // the blog's topics that have posts
        List<BlogService.PublicPost> cards = blogService.publishedCards();
        for (BlogTopics.Topic topic : BlogTopics.ALL) {
            if (!BlogTopics.postsOf(topic, cards).isEmpty()) {
                xml.append("  <url><loc>").append(siteUrl).append("/blog/topic/").append(topic.slug()).append("</loc></url>\n");
            }
        }
        for (BlogPost post : blogService.published()) {
            xml.append("  <url><loc>").append(siteUrl).append("/blog/").append(post.getSlug()).append("</loc>");
            String changed = lastChanged(post);
            if (changed != null) {
                xml.append("<lastmod>").append(changed).append("</lastmod>");
            }
            xml.append("</url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    /** The day a post last really changed: its last edit, or its publishing if that came later. */
    static String lastChanged(BlogPost post) {
        LocalDate published = post.getPublishedAt() == null ? null
                : post.getPublishedAt().atZone(ZoneId.of("Asia/Tehran")).toLocalDate();
        LocalDate edited = post.getUpdatedAt() == null ? null : post.getUpdatedAt().toLocalDate();
        LocalDate day = published == null ? edited : edited == null || edited.isBefore(published) ? published : edited;
        return day == null ? null : day.toString();
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
