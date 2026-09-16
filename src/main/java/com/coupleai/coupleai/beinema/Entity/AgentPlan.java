package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private Integer durationDays;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
