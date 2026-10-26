package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlogWriterTest {

    private static final List<BlogWriter.Topic> TOPICS = BlogWriter.parseTopics("""
            # key|column|subject
            ct-a|ct|الف
            ct-b|ct|ب
            com-a|com|پ
            life-a|life|ت
            """);

    private static BlogPost post(String key) {
        BlogPost post = new BlogPost();
        post.setTopicKey(key);
        post.setTitle(key);
        return post;
    }

    @Test
    void theFirstPostTakesTheFirstSubject() {
        assertEquals("ct-a", BlogWriter.nextTopic(TOPICS, List.of()).key());
    }

    @Test
    void neverTheSameColumnTwiceAndTheLongestWaitingColumnFirst() {
        // newest first: yesterday ct, before that com; life never written
        assertEquals("life-a", BlogWriter.nextTopic(TOPICS, List.of(post("ct-a"), post("com-a"))).key());
        // life and com written after ct: back to ct, and its unwritten subject
        assertEquals("ct-b", BlogWriter.nextTopic(TOPICS, List.of(post("life-a"), post("com-a"), post("ct-a"))).key());
    }

    @Test
    void whenAColumnIsUsedUpItsOldestSubjectComesBack() {
        List<BlogPost> recent = List.of(post("life-a"), post("com-a"), post("ct-a"), post("ct-b"));
        assertEquals("ct-b", BlogWriter.nextTopic(TOPICS, recent).key());
    }

    @Test
    void theCalendarFileIsRead() throws Exception {
        String text = new String(new org.springframework.core.io.ClassPathResource("blog/topics.txt")
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(28, BlogWriter.parseTopics(text).size());
    }
}
