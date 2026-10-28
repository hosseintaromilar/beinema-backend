package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Entity.BlogPostVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The four picture options of a post version, made only when the admin asks,
 * from the admin's own description, in the blog's base style.
 */
@Service
@RequiredArgsConstructor
public class BlogImageGenerator {

    private final BlogService blogService;
    private final ImageStudio imageStudio;

    /** Returns the cost; the options are stored on the post's current version. */
    public double generate(Long postId, String adminDescription) {
        BlogPost post = blogService.view(postId).post();
        BlogPostVersion version = blogService.currentVersion(post);
        if (adminDescription == null || adminDescription.isBlank()) {
            throw new IllegalArgumentException("اول توصیف تصویر را بنویس");
        }
        String description = adminDescription.trim();
        ImageStudio.Result result = imageStudio.imagine(ImageStudio.Kind.BLOG, description);
        blogService.addImages(postId, version.getVersionNumber(), result.generationId(), result.urls());
        return result.cost();
    }
}
