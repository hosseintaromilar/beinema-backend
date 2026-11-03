package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How many buyers a discount step of one plan has had, out of its capacity. Taken only inside the
 * purchase's transaction with a conditional update, so the 101st buyer can never get step 1.
 * The public counter shows these real numbers; a refund does not give a place back.
 */
@Entity
@Table(name = "promo_tier_counters", uniqueConstraints = @UniqueConstraint(columnNames = {"plan", "tier_no"}))
@Getter
@Setter
@NoArgsConstructor
public class PromoTierCounter extends BaseEntity {

    @Column(length = 16, nullable = false)
    private String plan;

    @Column(name = "tier_no", nullable = false)
    private Integer tierNo;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer sold = 0;
}
