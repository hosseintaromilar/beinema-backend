package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The public blog of the site: only published posts. */
@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String siteUrl;

    @GetMapping
    public List<BlogPost> list() {
        return blogService.published();
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
    public BlogService.PostView post(@PathVariable String slug) {
        return blogService.publishedBySlug(slug);
    }
}
