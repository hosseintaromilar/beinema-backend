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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
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
    private final FileStorageService fileStorageService;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** What the writer workflow returns for one version. */
    public record Draft(String title, String slug, String summary, String bodyMarkdown, String tags,
                        String imagePrompt, String topicKey) {
    }

    public record PostView(BlogPost post, List<BlogPostVersion> versions, List<BlogImage> images) {
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
            chosen.setStoredPath(download(chosen.getSourceUrl()));
        }
        for (BlogImage image : imageRepository.findAllByPostId(postId)) {
            image.setChosen(image.getId().equals(chosen.getId()));
            imageRepository.save(image);
        }
        post.setImagePath(chosen.getStoredPath());
        return postRepository.save(post);
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
    public PostView publishedBySlug(String slug) {
        BlogPost post = postRepository.findBySlug(slug)
                .filter(item -> item.getStatus() == BlogPostStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalArgumentException("مطلب پیدا نشد."));
        return new PostView(post, List.of(currentVersion(post)), List.of());
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

    private String download(String url) {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("آدرس تصویر معتبر نیست.");
        }
        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("دریافت تصویر ممکن نشد (" + response.statusCode() + ").");
            }
            String type = response.headers().firstValue("Content-Type").orElse("");
            return fileStorageService.storeBlogImage(response.body(), type);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("دریافت تصویر ممکن نشد.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("دریافت تصویر قطع شد.");
        }
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
