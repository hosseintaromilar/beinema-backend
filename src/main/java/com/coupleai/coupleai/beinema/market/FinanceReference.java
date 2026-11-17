package com.coupleai.coupleai.beinema.market;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The reference figures no market sends us (bank deposit rates, the price of housing), as the admin
 * last checked them against their official source. One row; without it the file's version is used.
 */
@Entity
@Table(name = "finance_reference")
@Getter
@Setter
@NoArgsConstructor
public class FinanceReference extends BaseEntity {

    @Column(name = "data_json", columnDefinition = "TEXT", nullable = false)
    private String dataJson;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;
}
