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

/**
 * One visit to the site: from the first page until 30 minutes without activity. No address, no
 * name: the visitor is a random id the browser keeps, and times are Tehran local time so days and
 * hours group directly in SQL.
 */
@Entity
@Table(name = "visit_sessions", indexes = {
        @Index(name = "idx_visit_started", columnList = "started_at"),
        @Index(name = "idx_visit_visitor", columnList = "visitor_id"),
        @Index(name = "idx_visit_sid", columnList = "sid", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class VisitSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40, nullable = false)
    private String sid;

    @Column(name = "visitor_id", length = 40, nullable = false)
    private String visitorId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(nullable = false)
    private int pageviews;

    @Column(name = "entry_path", length = 200)
    private String entryPath;

    @Column(name = "exit_path", length = 200)
    private String exitPath;

    /** A key of {@link Traffic}: GOOGLE, INSTAGRAM, DIRECT, OTHER... */
    @Column(length = 20, nullable = false)
    private String source;

    @Column(name = "referrer_host", length = 120)
    private String referrerHost;

    @Column(name = "utm_source", length = 80)
    private String utmSource;

    @Column(name = "utm_medium", length = 80)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 80)
    private String utmCampaign;

    @Column(length = 10)
    private String device;

    @Column(length = 20)
    private String browser;

    @Column(name = "os", length = 20)
    private String system;

    @Column(name = "user_id")
    private Long userId;

    /** The first visit this browser ever made. */
    @Column(name = "new_visitor", nullable = false)
    private boolean newVisitor;
}
