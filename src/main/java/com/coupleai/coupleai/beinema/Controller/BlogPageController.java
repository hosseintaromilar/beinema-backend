package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Service.BlogPageHtml;
import com.coupleai.coupleai.beinema.Service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The blog's pages as complete HTML. The site's nginx sends /blog here and falls back to the
 * plain app on any failure, so a problem here never takes the blog down. The site's own
 * index.html is read every time, so a page always points at the files of the current deploy.
 */
@RestController
@RequestMapping(value = "/api/blog-pages", produces = "text/html;charset=UTF-8")
@RequiredArgsConstructor
public class BlogPageController {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();

    private final BlogService blogService;

    @Value("${app.frontend-url}")
    private String siteUrl;

    @Value("${app.site-shell-url}")
    private String shellUrl;

    @GetMapping
    public ResponseEntity<String> list() {
        String shell = shell();
        if (shell == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        return ResponseEntity.ok(BlogPageHtml.list(shell, siteUrl, blogService.publishedCards()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<String> post(@PathVariable String slug) {
        String shell = shell();
        if (shell == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        BlogService.PublicPost post;
        try {
            post = blogService.publishedBySlug(slug);
        } catch (IllegalArgumentException missing) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(BlogPageHtml.missing(shell));
        }
        return ResponseEntity.ok(BlogPageHtml.post(shell, siteUrl, post, blogService.publishedCards()));
    }

    private String shell() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(shellUrl)).timeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (IOException failure) {
            return null;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
