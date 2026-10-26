package com.coupleai.coupleai.beinema.Enum;

/**
 * Where a blog post stands. The daily writer leaves a DRAFT for the admin, who
 * publishes it, sends it back with notes (REVISING until the next version comes)
 * or rejects it.
 */
public enum BlogPostStatus {
    DRAFT,
    REVISING,
    PUBLISHED,
    REJECTED
}
