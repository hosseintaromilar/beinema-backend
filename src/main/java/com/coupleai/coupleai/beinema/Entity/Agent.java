package com.coupleai.coupleai.beinema.Entity;

import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent extends BaseEntity {


    @Column(nullable = false)
    private String name;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentType type;


    @Column(length = 2000)
    private String description;


    @Column(length = 10000)
    private String systemPrompt;


    private String avatar;


    @Column(length = 255)
    private String headline;


    @Column(columnDefinition = "TEXT")
    private String bio;


    @Column(length = 1000)
    private String specialties;


    @Column(length = 500)
    private String photoUrl;


    /**
     * شناسه Bot در سرویس AI Provider
     *
     * در حال حاضر:
     * Metis Bot ID
     *
     * این مقدار مربوط به خود Agent است،
     * نه یک Conversation خاص.
     */
    @Column(name = "ai_bot_id", nullable = false)
    private String aiBotId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AgentStatus status = AgentStatus.ACTIVE;

}