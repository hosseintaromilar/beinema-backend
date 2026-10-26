package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.BlogPostStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A blog post written by the daily writer workflow. Its text lives in versions
 * (the first draft, then one per round of the admin's notes); the post points at
 * the current one and carries what the site shows: slug, title, summary, image.
 */
@Entity
@Table(name = "blog_post", indexes = {
        @Index(name = "uk_blog_post_slug", columnList = "slug", unique = true),
        @Index(name = "idx_blog_post_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class BlogPost extends BaseEntity {

    /** The address of the post on the site (latin, dashes). */
    @Column(name = "slug", nullable = false, length = 160)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BlogPostStatus status = BlogPostStatus.DRAFT;

    /** The topic of the calendar it was written from (docs/blog-topics.md). */
    @Column(name = "topic_key", length = 64)
    private String topicKey;

    /** The number of the version shown to the admin and, once published, on the site. */
    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** The meta description and the teaser in the list. */
    @Column(name = "summary", length = 400)
    private String summary;

    /** The picture the admin chose, stored under /uploads (never the generator's temporary link). */
    @Column(name = "image_path", length = 300)
    private String imagePath;

    @Column(name = "published_at")
    private Instant publishedAt;
}
