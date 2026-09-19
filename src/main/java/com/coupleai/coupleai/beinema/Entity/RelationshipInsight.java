package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "relationship_insights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelationshipInsight extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false, unique = true)
    private ChatRoom chatRoom;

    @Column(columnDefinition = "TEXT")
    private String whereWeWere;

    @Column(columnDefinition = "TEXT")
    private String whereWeAre;

    @Column(length = 32)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String needsJson;

    @Column(columnDefinition = "TEXT")
    private String patternsJson;

    @Column(columnDefinition = "TEXT")
    private String nextStep;

    @Column(columnDefinition = "TEXT")
    private String dailySentence;

    private LocalDateTime analyzedAt;

    @Builder.Default
    private Integer messageCountAtAnalysis = 0;

    private String analysisSessionId;
}
