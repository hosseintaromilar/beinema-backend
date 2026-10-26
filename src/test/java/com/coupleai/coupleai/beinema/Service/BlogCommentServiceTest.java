package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogComment;
import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Enum.BlogCommentStatus;
import com.coupleai.coupleai.beinema.Repository.BlogCommentRepository;
import com.coupleai.coupleai.beinema.Repository.BlogPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlogCommentServiceTest {

    private BlogCommentRepository comments;
    private BlogCommentService service;

    @BeforeEach
    void setUp() {
        comments = mock(BlogCommentRepository.class);
        BlogService blog = mock(BlogService.class);
        BlogPost post = new BlogPost();
        ReflectionTestUtils.setField(post, "id", 9L);
        when(blog.publishedPost("five-needs")).thenReturn(post);
        service = new BlogCommentService(comments, mock(BlogPostRepository.class), blog);
    }

    @Test
    void aCommentWaitsForTheAdminAndKeepsNoPlainAddress() {
        service.submit("five-needs", " سارا ", " خیلی به درد خورد ", "", "5.6.7.8");

        ArgumentCaptor<BlogComment> saved = ArgumentCaptor.forClass(BlogComment.class);
        verify(comments).save(saved.capture());
        assertEquals(BlogCommentStatus.PENDING, saved.getValue().getStatus());
        assertEquals("سارا", saved.getValue().getAuthorName());
        assertEquals(9L, saved.getValue().getPostId());
        assertNotEquals("5.6.7.8", saved.getValue().getIpHash());
    }

    @Test
    void emptyLongAndBotCommentsAreNotSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.submit("five-needs", "", "متن", "", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit("five-needs", "نام", "x".repeat(BlogCommentService.MAX_BODY + 1), "", "1"));
        service.submit("five-needs", "bot", "spam", "http://spam", "1");
        verify(comments, never()).save(any());
    }

    @Test
    void oneAddressSendsAFewCommentsPerTenMinutes() {
        for (int i = 0; i < BlogCommentService.PER_WINDOW; i++) {
            assertTrue(service.allow("a", 1000L + i));
        }
        assertFalse(service.allow("a", 2000L));
        assertTrue(service.allow("b", 2000L), "another address is not affected");
        assertTrue(service.allow("a", 2000L + BlogCommentService.WINDOW_MILLIS), "after the window it opens again");
    }
}
