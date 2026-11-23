package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One line of a household's budget book: money in or out, its category, the day and a short note.
 * The book belongs to the conversation with Pouria, so both people of the household see it; each line
 * says who wrote it, or that Pouria wrote it from what they said.
 */
@Entity
@Table(name = "budget_entries",
        indexes = @Index(name = "idx_budget_room_day", columnList = "chat_room_id, day"),
        uniqueConstraints = @UniqueConstraint(name = "uk_budget_room_receipt", columnNames = {"chat_room_id", "receipt_sha256"}))
@Getter
@Setter
@NoArgsConstructor
public class BudgetEntry extends BaseEntity {

    public static final String PERSON = "PERSON";
    public static final String POURIA = "POURIA";

    @Column(name = "public_id", length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    /** INCOME or EXPENSE. */
    @Column(length = 10, nullable = false)
    private String kind;

    @Column(length = 20, nullable = false)
    private String category;

    /** In tomans. */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private LocalDate day;

    @Column(length = 200)
    private String note;

    /** Who wrote it; null when Pouria did. */
    @Column(name = "created_by")
    private Long createdBy;

    /** PERSON or POURIA. */
    @Column(length = 10, nullable = false)
    private String source;

    @Column(name = "receipt_sha256", length = 64)
    private String receiptSha256;
}
