package com.coupleai.coupleai.beinema.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One page opened in a visit. Tehran local time. */
@Entity
@Table(name = "page_views", indexes = {
        @Index(name = "idx_view_at", columnList = "viewed_at"),
        @Index(name = "idx_view_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PageView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "visitor_id", length = 40, nullable = false)
    private String visitorId;

    @Column(length = 200, nullable = false)
    private String path;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
