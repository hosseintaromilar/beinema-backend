package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RelationshipInsightResponse {

    private Long chatRoomId;

    private boolean empty;

    private String whereWeWere;

    private String whereWeAre;

    private String status;

    private List<String> needs;

    private List<String> patterns;

    private String nextStep;

    private String dailySentence;

    private Long analyzedAt;

    private String message;
}
