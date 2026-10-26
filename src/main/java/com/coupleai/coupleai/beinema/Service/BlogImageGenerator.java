package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Entity.BlogPostVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The four picture options of a post version: the admin's own description, or
 * else the writer's picture prompt, in the blog's base style.
 */
@Service
@RequiredArgsConstructor
public class BlogImageGenerator {

    private static final String FALLBACK = "A couple sitting together at a kitchen table, talking calmly, seen from behind";

    private final BlogService blogService;
    private final ImageStudio imageStudio;

    /** The writer's own picture prompt. */
    public double generate(Long postId) {
        return generate(postId, null);
    }

    /** Returns the cost; the options are stored on the post's current version. */
    public double generate(Long postId, String adminDescription) {
        BlogPost post = blogService.view(postId).post();
        BlogPostVersion version = blogService.currentVersion(post);
        String description = adminDescription != null && !adminDescription.isBlank()
                ? adminDescription
                : version.getImagePrompt() != null && !version.getImagePrompt().isBlank()
                ? version.getImagePrompt()
                : FALLBACK;
        ImageStudio.Result result = imageStudio.imagine(ImageStudio.Kind.BLOG, description);
        blogService.addImages(postId, version.getVersionNumber(), result.generationId(), result.urls());
        return result.cost();
    }
}
