package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "referrals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_referrals_referred_user",
                        columnNames = "referred_user_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Referral extends BaseEntity {

    @Column(name = "referrer_user_id", nullable = false)
    private Long referrerUserId;

    @Column(name = "referred_user_id", nullable = false)
    private Long referredUserId;

    @Column(nullable = false, length = 12)
    private String code;

    @Column(nullable = false)
    private Long referrerAmount;

    @Column(nullable = false)
    private Long refereeAmount;
}
