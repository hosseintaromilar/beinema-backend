package com.coupleai.coupleai.beinema.lessonreview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LessonReviewRepository extends JpaRepository<LessonReview, Long> {

    Optional<LessonReview> findByLessonId(String lessonId);
}
