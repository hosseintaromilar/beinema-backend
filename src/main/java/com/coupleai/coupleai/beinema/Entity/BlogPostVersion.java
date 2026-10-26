package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One written version of a post. The admin's notes on a version are kept on it;
 * the writer answers them with the next version.
 */
@Entity
@Table(name = "blog_post_version", indexes = @Index(name = "idx_blog_version_post", columnList = "post_id,version_number"))
@Getter
@Setter
@NoArgsConstructor
public class BlogPostVersion extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 400)
    private String summary;

    @Column(name = "body_markdown", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String bodyMarkdown;

    /** Comma separated. */
    @Column(name = "tags", length = 300)
    private String tags;

    /** The English prompt for the four pictures (no text in the picture). */
    @Column(name = "image_prompt", length = 1000)
    private String imagePrompt;

    /** What the admin asked to change in this version, or null. */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;
}
