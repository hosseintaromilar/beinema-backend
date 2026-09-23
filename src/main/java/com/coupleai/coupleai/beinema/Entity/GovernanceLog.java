package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.governance.GovernanceAction;
import com.coupleai.coupleai.beinema.governance.GovernanceTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "governance_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GovernanceLog extends BaseEntity {

    @Column(nullable = false)
    private Long chatRoomId;

    private Long participantUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GovernanceAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GovernanceTarget target;

    private Integer durationSeconds;

    @Column(length = 40)
    private String previousState;

    @Column(length = 40)
    private String newState;

    @Column(length = 180)
    private String reason;
}
