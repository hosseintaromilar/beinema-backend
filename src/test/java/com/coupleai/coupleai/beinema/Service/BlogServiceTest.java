package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Entity.BlogPostVersion;
import com.coupleai.coupleai.beinema.Enum.BlogPostStatus;
import com.coupleai.coupleai.beinema.Repository.BlogImageRepository;
import com.coupleai.coupleai.beinema.Repository.BlogPostRepository;
import com.coupleai.coupleai.beinema.Repository.BlogPostVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlogServiceTest {

    private BlogPostRepository posts;
    private BlogPostVersionRepository versions;
    private BlogService service;

    private static final BlogService.Draft DRAFT = new BlogService.Draft(
            "پنج نیاز اساسی", "Five Basic Needs!", "خلاصه", "متن مطلب", "نیاز,رابطه",
            "Two cups of tea on a table, warm light, no text", "ct-basic-needs");

    @BeforeEach
    void setUp() {
        posts = mock(BlogPostRepository.class);
        versions = mock(BlogPostVersionRepository.class);
        service = new BlogService(posts, versions, mock(BlogImageRepository.class), mock(FileStorageService.class));
        when(posts.save(any(BlogPost.class))).thenAnswer(call -> {
            BlogPost post = call.getArgument(0);
            if (post.getId() == null) {
                ReflectionTestUtils.setField(post, "id", 5L);
            }
            return post;
        });
    }

    private BlogPost post(BlogPostStatus status) {
        BlogPost post = new BlogPost();
        ReflectionTestUtils.setField(post, "id", 5L);
        post.setStatus(status);
        post.setCurrentVersion(1);
        post.setTitle("t");
        post.setSlug("t");
        when(posts.findById(5L)).thenReturn(Optional.of(post));
        return post;
    }

    @Test
    void slugsAreLatinAndUnique() {
        assertEquals("five-basic-needs", BlogService.slugOf("Five Basic Needs!", "x"));
        assertTrue(BlogService.slugOf("", "فقط فارسی").startsWith("post-"));
        when(posts.existsBySlug("five-basic-needs")).thenReturn(true);

        BlogPost created = service.createDraft(DRAFT);

        assertEquals("five-basic-needs-2", created.getSlug());
        assertEquals(BlogPostStatus.DRAFT, created.getStatus());
        ArgumentCaptor<BlogPostVersion> saved = ArgumentCaptor.forClass(BlogPostVersion.class);
        verify(versions).save(saved.capture());
        assertEquals(1, saved.getValue().getVersionNumber());
    }

    @Test
    void notesSendItBackAndTheNextVersionComesAsADraft() {
        BlogPost post = post(BlogPostStatus.DRAFT);
        post.setImagePath("/uploads/blog/a.webp");
        BlogPostVersion first = new BlogPostVersion();
        when(versions.findByPostIdAndVersionNumber(5L, 1)).thenReturn(Optional.of(first));

        assertThrows(IllegalArgumentException.class, () -> service.requestRevision(5L, "  "));
        service.requestRevision(5L, "مثال‌ها ایرانی‌تر باشد");
        assertEquals(BlogPostStatus.REVISING, post.getStatus());
        assertEquals("مثال‌ها ایرانی‌تر باشد", first.getAdminNote());

        service.addRevision(5L, DRAFT);
        assertEquals(2, post.getCurrentVersion());
        assertEquals(BlogPostStatus.DRAFT, post.getStatus());
        assertNull(post.getImagePath(), "the picture is chosen again for the new version");
    }

    @Test
    void publishingNeedsAChosenPictureAndOnlyDraftsArePublished() {
        BlogPost post = post(BlogPostStatus.DRAFT);
        assertThrows(IllegalArgumentException.class, () -> service.publish(5L));

        post.setImagePath("/uploads/blog/a.webp");
        service.publish(5L);
        assertEquals(BlogPostStatus.PUBLISHED, post.getStatus());
        assertTrue(post.getPublishedAt() != null);

        assertThrows(IllegalArgumentException.class, () -> service.publish(5L));
        assertThrows(IllegalArgumentException.class, () -> service.reject(5L));
    }

    @Test
    void theSiteShowsOnlyPublishedPosts() {
        BlogPost draft = post(BlogPostStatus.DRAFT);
        when(posts.findBySlug("t")).thenReturn(Optional.of(draft));
        assertThrows(IllegalArgumentException.class, () -> service.publishedBySlug("t"));
    }
}
