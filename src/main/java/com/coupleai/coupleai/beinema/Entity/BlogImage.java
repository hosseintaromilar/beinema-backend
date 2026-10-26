package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One of the four picture variants made for a post version; the admin chooses one.
 */
@Entity
@Table(name = "blog_image", indexes = @Index(name = "idx_blog_image_post", columnList = "post_id"))
@Getter
@Setter
@NoArgsConstructor
public class BlogImage extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** The Metis generation it came from. */
    @Column(name = "generation_id", length = 64)
    private String generationId;

    /** The generator's link (temporary). */
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    /** Our own copy under /uploads, once stored. */
    @Column(name = "stored_path", length = 300)
    private String storedPath;

    @Column(name = "chosen", nullable = false)
    private boolean chosen;
}
