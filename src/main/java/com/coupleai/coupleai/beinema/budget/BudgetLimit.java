package com.coupleai.coupleai.beinema.budget;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How much a household means to spend in one category each month (tomans), as they agreed. */
@Entity
@Table(name = "budget_limits", uniqueConstraints = @UniqueConstraint(name = "uk_budget_limit", columnNames = {"chat_room_id", "category"}))
@Getter
@Setter
@NoArgsConstructor
public class BudgetLimit extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(length = 20, nullable = false)
    private String category;

    @Column(nullable = false)
    private long amount;
}
