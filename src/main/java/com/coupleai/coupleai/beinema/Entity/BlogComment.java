package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.BlogCommentStatus;
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

/** A reader's comment on a blog post; the site shows it only after the admin approves it. */
@Entity
@Table(name = "blog_comment", indexes = {
        @Index(name = "idx_blog_comment_post", columnList = "post_id,status"),
        @Index(name = "idx_blog_comment_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class BlogComment extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_name", nullable = false, length = 60)
    private String authorName;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BlogCommentStatus status = BlogCommentStatus.PENDING;

    /** A one-way hash of the sender's address, only to slow down floods. */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
