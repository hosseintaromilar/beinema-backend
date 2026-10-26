package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Enum.BlogPostStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    Optional<BlogPost> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<BlogPost> findAllByOrderByIdDesc();

    List<BlogPost> findAllByStatusOrderByPublishedAtDesc(BlogPostStatus status);

    /** Titles already written, so the writer does not repeat a subject. */
    List<BlogPost> findTop60ByOrderByIdDesc();
}
