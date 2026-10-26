package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.BlogPostVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogPostVersionRepository extends JpaRepository<BlogPostVersion, Long> {

    Optional<BlogPostVersion> findByPostIdAndVersionNumber(Long postId, int versionNumber);

    List<BlogPostVersion> findAllByPostIdOrderByVersionNumberAsc(Long postId);
}
