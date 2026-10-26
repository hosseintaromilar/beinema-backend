package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogComment;
import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Enum.BlogCommentStatus;
import com.coupleai.coupleai.beinema.Repository.BlogCommentRepository;
import com.coupleai.coupleai.beinema.Repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Readers' comments on blog posts. Anyone may send one (a name and the text);
 * it waits for the admin and the site shows only approved ones. A hidden field
 * catches simple bots and one address may send a few comments per ten minutes.
 */
@Service
@RequiredArgsConstructor
public class BlogCommentService {

    static final int MAX_NAME = 60;
    static final int MAX_BODY = 1500;
    static final int PER_WINDOW = 5;
    static final long WINDOW_MILLIS = 10 * 60 * 1000L;

    private final BlogCommentRepository commentRepository;
    private final BlogPostRepository postRepository;
    private final BlogService blogService;
    private final Map<String, Deque<Long>> recentByAddress = new ConcurrentHashMap<>();

    /** What the site shows of an approved comment. */
    public record PublicComment(Long id, String name, String body, Long createdAt) {
    }

    /** What the admin sees. */
    public record AdminComment(Long id, Long postId, String postTitle, String postSlug, String name, String body,
                               String status, Long createdAt) {
    }

    public List<PublicComment> approved(String slug) {
        BlogPost post = blogService.publishedPost(slug);
        return commentRepository.findAllByPostIdAndStatusOrderByIdAsc(post.getId(), BlogCommentStatus.APPROVED)
                .stream()
                .map(comment -> new PublicComment(comment.getId(), comment.getAuthorName(), comment.getBody(),
                        millis(comment)))
                .toList();
    }

    /** A reader's comment; returns quietly for a bot that filled the hidden field. */
    @Transactional
    public void submit(String slug, String name, String body, String honeypot, String address) {
        BlogPost post = blogService.publishedPost(slug);
        if (honeypot != null && !honeypot.isBlank()) {
            return;
        }
        String who = name == null ? "" : name.trim();
        String text = body == null ? "" : body.trim();
        if (who.isEmpty() || text.isEmpty()) {
            throw new IllegalArgumentException("نام و متن نظر را بنویس.");
        }
        if (who.length() > MAX_NAME || text.length() > MAX_BODY) {
            throw new IllegalArgumentException("نام یا متن نظر خیلی طولانی است.");
        }
        String hash = hash(address);
        if (!allow(hash, System.currentTimeMillis())) {
            throw new IllegalArgumentException("چند دقیقهٔ دیگر دوباره امتحان کن.");
        }
        BlogComment comment = new BlogComment();
        comment.setPostId(post.getId());
        comment.setAuthorName(who);
        comment.setBody(text);
        comment.setIpHash(hash);
        comment.setStatus(BlogCommentStatus.PENDING);
        commentRepository.save(comment);
    }

    public List<AdminComment> forAdmin(String status) {
        List<BlogComment> comments = status == null || status.isBlank()
                ? commentRepository.findTop200ByOrderByIdDesc()
                : commentRepository.findAllByStatusOrderByIdDesc(BlogCommentStatus.valueOf(status));
        Map<Long, BlogPost> posts = new java.util.HashMap<>();
        return comments.stream().map(comment -> {
            BlogPost post = posts.computeIfAbsent(comment.getPostId(), id -> postRepository.findById(id).orElse(null));
            return new AdminComment(comment.getId(), comment.getPostId(),
                    post == null ? "(حذف‌شده)" : post.getTitle(), post == null ? null : post.getSlug(),
                    comment.getAuthorName(), comment.getBody(), comment.getStatus().name(), millis(comment));
        }).toList();
    }

    @Transactional
    public void review(Long commentId, BlogCommentStatus status) {
        BlogComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("نظر پیدا نشد."));
        comment.setStatus(status);
        comment.setReviewedAt(Instant.now());
        commentRepository.save(comment);
    }

    @Transactional
    public void delete(Long commentId) {
        commentRepository.deleteById(commentId);
    }

    public long pendingCount() {
        return commentRepository.countByStatus(BlogCommentStatus.PENDING);
    }

    /** At most {@link #PER_WINDOW} comments per address in ten minutes. */
    boolean allow(String addressHash, long now) {
        Deque<Long> times = recentByAddress.computeIfAbsent(addressHash, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MILLIS) {
                times.pollFirst();
            }
            if (times.size() >= PER_WINDOW) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    private static Long millis(BlogComment comment) {
        return comment.getCreatedAt() == null ? null
                : comment.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String hash(String address) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("beinema-comment:" + (address == null ? "" : address)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
