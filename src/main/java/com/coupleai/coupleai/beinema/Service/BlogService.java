package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogImage;
import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Entity.BlogPostVersion;
import com.coupleai.coupleai.beinema.Enum.BlogPostStatus;
import com.coupleai.coupleai.beinema.Repository.BlogImageRepository;
import com.coupleai.coupleai.beinema.Repository.BlogPostRepository;
import com.coupleai.coupleai.beinema.Repository.BlogPostVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * The blog: the writer's drafts and their versions, the admin's review (publish,
 * send back with notes, reject, choose one of the four pictures) and the posts
 * the site shows.
 */
@Service
@RequiredArgsConstructor
public class BlogService {

    private static final int MAX_NOTE = 4000;

    private final BlogPostRepository postRepository;
    private final BlogPostVersionRepository versionRepository;
    private final BlogImageRepository imageRepository;
    private final ImageStudio imageStudio;

    /** What the writer workflow returns for one version. */
    public record Draft(String title, String slug, String summary, String bodyMarkdown, String tags,
                        String imagePrompt, String topicKey) {
    }

    public record PostView(BlogPost post, List<BlogPostVersion> versions, List<BlogImage> images) {
    }

    /** A published post as the site shows it; tags and text only on the post's own page. */
    public record PublicPost(Long id, String slug, String title, String summary, String imagePath,
                             Long publishedAt, String tags, String bodyMarkdown) {
    }

    /** A new post from the writer: version 1, waiting for the admin. */
    @Transactional
    public BlogPost createDraft(Draft draft) {
        requireText(draft);
        BlogPost post = new BlogPost();
        post.setSlug(uniqueSlug(draft.slug(), draft.title()));
        post.setTopicKey(draft.topicKey());
        post.setStatus(BlogPostStatus.DRAFT);
        post.setCurrentVersion(1);
        copyHeadline(post, draft);
        post = postRepository.save(post);
        versionRepository.save(version(post.getId(), 1, draft));
        return post;
    }

    /** The writer's answer to the admin's notes: the next version, back to DRAFT. */
    @Transactional
    public BlogPost addRevision(Long postId, Draft draft) {
        requireText(draft);
        BlogPost post = find(postId);
        if (post.getStatus() != BlogPostStatus.REVISING) {
            throw new IllegalArgumentException("این مطلب منتظر اصلاح نیست.");
        }
        int next = post.getCurrentVersion() + 1;
        versionRepository.save(version(postId, next, draft));
        post.setCurrentVersion(next);
        post.setStatus(BlogPostStatus.DRAFT);
        copyHeadline(post, draft);
        // the picture belongs to the old version; the admin chooses again
        post.setImagePath(null);
        return postRepository.save(post);
    }

    /** The four pictures made for a version (links of the generator, stored only when chosen). */
    @Transactional
    public void addImages(Long postId, int versionNumber, String generationId, List<String> urls) {
        find(postId);
        for (String url : urls) {
            BlogImage image = new BlogImage();
            image.setPostId(postId);
            image.setVersionNumber(versionNumber);
            image.setGenerationId(generationId);
            image.setSourceUrl(url);
            imageRepository.save(image);
        }
    }

    public List<BlogPost> all() {
        return postRepository.findAllByOrderByIdDesc();
    }

    public PostView view(Long postId) {
        BlogPost post = find(postId);
        return new PostView(post,
                versionRepository.findAllByPostIdOrderByVersionNumberAsc(postId),
                imageRepository.findAllByPostIdAndVersionNumberOrderByIdAsc(postId, post.getCurrentVersion()));
    }

    /** The admin chooses one picture: it is copied to our own storage. */
    @Transactional
    public BlogPost chooseImage(Long postId, Long imageId) {
        BlogPost post = find(postId);
        BlogImage chosen = imageRepository.findById(imageId)
                .filter(image -> image.getPostId().equals(postId))
                .orElseThrow(() -> new IllegalArgumentException("تصویر پیدا نشد."));
        if (chosen.getStoredPath() == null) {
            chosen.setStoredPath(imageStudio.download(chosen.getSourceUrl(), "blog"));
        }
        for (BlogImage image : imageRepository.findAllByPostId(postId)) {
            image.setChosen(image.getId().equals(chosen.getId()));
            imageRepository.save(image);
        }
        post.setImagePath(chosen.getStoredPath());
        return postRepository.save(post);
    }

    /** The admin's own edit of the current version (title, summary, text, tags; the address only before publishing). */
    @Transactional
    public PostView edit(Long postId, Draft edit) {
        requireText(edit);
        BlogPost post = find(postId);
        if (post.getStatus() == BlogPostStatus.REJECTED || post.getStatus() == BlogPostStatus.REVISING) {
            throw new IllegalArgumentException("این مطلب الان ویرایش نمی‌شود.");
        }
        BlogPostVersion current = currentVersion(post);
        current.setTitle(clip(edit.title(), 200));
        current.setSummary(clip(edit.summary(), 400));
        current.setBodyMarkdown(edit.bodyMarkdown().trim());
        current.setTags(clip(edit.tags(), 300));
        versionRepository.save(current);
        copyHeadline(post, edit);
        if (post.getStatus() != BlogPostStatus.PUBLISHED && edit.slug() != null && !edit.slug().isBlank()) {
            String wanted = slugOf(edit.slug(), edit.title());
            if (!wanted.equals(post.getSlug())) {
                post.setSlug(uniqueSlug(wanted, edit.title()));
            }
        }
        postRepository.save(post);
        return view(postId);
    }

    @Transactional
    public BlogPost publish(Long postId) {
        BlogPost post = find(postId);
        if (post.getStatus() != BlogPostStatus.DRAFT) {
            throw new IllegalArgumentException("فقط پیش‌نویس منتشر می‌شود.");
        }
        if (post.getImagePath() == null) {
            throw new IllegalArgumentException("اول یکی از تصویرها را انتخاب کن.");
        }
        post.setStatus(BlogPostStatus.PUBLISHED);
        post.setPublishedAt(Instant.now());
        return postRepository.save(post);
    }

    /** Sends the draft back to the writer with what to change. */
    @Transactional
    public BlogPost requestRevision(Long postId, String note) {
        BlogPost post = find(postId);
        String text = note == null ? "" : note.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("بنویس چه چیزی باید عوض شود.");
        }
        if (text.length() > MAX_NOTE) {
            throw new IllegalArgumentException("یادداشت خیلی طولانی است.");
        }
        if (post.getStatus() != BlogPostStatus.DRAFT) {
            throw new IllegalArgumentException("فقط پیش‌نویس برای اصلاح برمی‌گردد.");
        }
        BlogPostVersion current = currentVersion(post);
        current.setAdminNote(text);
        versionRepository.save(current);
        post.setStatus(BlogPostStatus.REVISING);
        return postRepository.save(post);
    }

    @Transactional
    public BlogPost reject(Long postId) {
        BlogPost post = find(postId);
        if (post.getStatus() == BlogPostStatus.PUBLISHED) {
            throw new IllegalArgumentException("مطلب منتشرشده رد نمی‌شود.");
        }
        post.setStatus(BlogPostStatus.REJECTED);
        return postRepository.save(post);
    }

    /** The site: published posts, newest first. */
    public List<BlogPost> published() {
        return postRepository.findAllByStatusOrderByPublishedAtDesc(BlogPostStatus.PUBLISHED);
    }

    /** The site: one published post and the text of its current version. */
    public PublicPost publishedBySlug(String slug) {
        BlogPost post = publishedPost(slug);
        BlogPostVersion text = currentVersion(post);
        return new PublicPost(post.getId(), post.getSlug(), post.getTitle(), post.getSummary(), post.getImagePath(),
                millis(post.getPublishedAt()), text.getTags(), text.getBodyMarkdown());
    }

    /** The site's list: only what a reader sees (no notes, prompts or versions). */
    public List<PublicPost> publishedCards() {
        return published().stream()
                .map(post -> new PublicPost(post.getId(), post.getSlug(), post.getTitle(), post.getSummary(),
                        post.getImagePath(), millis(post.getPublishedAt()), null, null))
                .toList();
    }

    public BlogPost publishedPost(String slug) {
        return postRepository.findBySlug(slug)
                .filter(item -> item.getStatus() == BlogPostStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalArgumentException("مطلب پیدا نشد."));
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    public BlogPostVersion currentVersion(BlogPost post) {
        return versionRepository.findByPostIdAndVersionNumber(post.getId(), post.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException("نسخهٔ مطلب پیدا نشد."));
    }

    /** Latin, lowercase, dashes; Persian-only titles fall back to "post-<time>". */
    static String slugOf(String wanted, String title) {
        String base = wanted != null && !wanted.isBlank() ? wanted : "";
        base = Normalizer.normalize(base, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (base.length() > 80) {
            base = base.substring(0, 80).replaceAll("-+$", "");
        }
        return base.isEmpty() ? "post-" + Instant.now().getEpochSecond() : base;
    }

    private String uniqueSlug(String wanted, String title) {
        String base = slugOf(wanted, title);
        String slug = base;
        for (int i = 2; postRepository.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private BlogPost find(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("مطلب پیدا نشد."));
    }

    private static void requireText(Draft draft) {
        if (draft == null || draft.title() == null || draft.title().isBlank()
                || draft.bodyMarkdown() == null || draft.bodyMarkdown().isBlank()) {
            throw new IllegalArgumentException("متن مطلب کامل نیست.");
        }
    }

    private static void copyHeadline(BlogPost post, Draft draft) {
        post.setTitle(clip(draft.title(), 200));
        post.setSummary(clip(draft.summary(), 400));
    }

    private static BlogPostVersion version(Long postId, int number, Draft draft) {
        BlogPostVersion version = new BlogPostVersion();
        version.setPostId(postId);
        version.setVersionNumber(number);
        version.setTitle(clip(draft.title(), 200));
        version.setSummary(clip(draft.summary(), 400));
        version.setBodyMarkdown(draft.bodyMarkdown().trim());
        version.setTags(clip(draft.tags(), 300));
        version.setImagePrompt(clip(draft.imagePrompt(), 1000));
        return version;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
