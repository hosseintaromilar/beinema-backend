package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.BlogImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlogImageRepository extends JpaRepository<BlogImage, Long> {

    List<BlogImage> findAllByPostIdAndVersionNumberOrderByIdAsc(Long postId, int versionNumber);

    List<BlogImage> findAllByPostId(Long postId);
}
