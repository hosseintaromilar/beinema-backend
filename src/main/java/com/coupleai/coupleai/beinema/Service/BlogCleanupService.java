package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogImage;
import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Enum.BlogPostStatus;
import com.coupleai.coupleai.beinema.Repository.BlogCommentRepository;
import com.coupleai.coupleai.beinema.Repository.BlogImageRepository;
import com.coupleai.coupleai.beinema.Repository.BlogPostRepository;
import com.coupleai.coupleai.beinema.Repository.BlogPostVersionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Removing what the blog no longer needs, so the server does not fill up and the editor loads
 * light: a rejected post with everything of it (versions, picture options and their files,
 * comments), or one picture option the admin does not want (never the chosen one).
 */
@Service
@RequiredArgsConstructor
public class BlogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(BlogCleanupService.class);

    private final BlogPostRepository postRepository;
    private final BlogPostVersionRepository versionRepository;
    private final BlogImageRepository imageRepository;
    private final BlogCommentRepository commentRepository;
    private final FileStorageService fileStorageService;

    /** Only a rejected post can be removed; it is gone for good, with its pictures' files. */
    @Transactional
    public void deletePost(Long postId) {
        BlogPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("مطلب پیدا نشد."));
        if (post.getStatus() != BlogPostStatus.REJECTED) {
            throw new IllegalArgumentException("فقط مطلب ردشده حذف می‌شود؛ اول ردش کن.");
        }
        List<BlogImage> images = imageRepository.findAllByPostId(postId);
        imageRepository.deleteAll(images);
        commentRepository.deleteAll(commentRepository.findAllByPostId(postId));
        versionRepository.deleteAll(versionRepository.findAllByPostIdOrderByVersionNumberAsc(postId));
        postRepository.delete(post);
        images.forEach(image -> removeFile(image.getStoredPath()));
        removeFile(post.getImagePath());
        log.info("Blog post {} ({}) removed with {} picture options", postId, post.getSlug(), images.size());
    }

    /** One picture option the admin does not want; the post's chosen picture stays. */
    @Transactional
    public void deleteImage(Long postId, Long imageId) {
        BlogPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("مطلب پیدا نشد."));
        BlogImage image = imageRepository.findById(imageId)
                .filter(found -> found.getPostId().equals(postId))
                .orElseThrow(() -> new IllegalArgumentException("تصویر پیدا نشد."));
        if (image.isChosen() || (image.getStoredPath() != null && image.getStoredPath().equals(post.getImagePath()))) {
            throw new IllegalArgumentException("تصویر شاخص فعلی حذف نمی‌شود؛ اول تصویر دیگری انتخاب کن.");
        }
        imageRepository.delete(image);
        removeFile(image.getStoredPath());
    }

    private void removeFile(String path) {
        if (path == null) {
            return;
        }
        // another option or post may still show the same file
        boolean used = imageRepository.existsByStoredPath(path) || postRepository.existsByImagePath(path);
        if (!used) {
            fileStorageService.deleteIfManaged(path);
        }
    }
}
