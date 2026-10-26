package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.BlogComment;
import com.coupleai.coupleai.beinema.Enum.BlogCommentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlogCommentRepository extends JpaRepository<BlogComment, Long> {

    List<BlogComment> findAllByPostIdAndStatusOrderByIdAsc(Long postId, BlogCommentStatus status);

    List<BlogComment> findAllByStatusOrderByIdDesc(BlogCommentStatus status);

    List<BlogComment> findTop200ByOrderByIdDesc();

    long countByStatus(BlogCommentStatus status);
}
