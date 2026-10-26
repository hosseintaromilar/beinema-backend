package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import lombok.Builder;
import lombok.Getter;


import java.util.List;

@Getter
@Builder
public class ChatRoomResponse {

    private Long id;

    private String title;

    private ChatRoomStatus status;

    private List<AgentSummary> agents;

    private List<ParticipantSummary> participants;

    private Long lastActivityAt;

    private Long createdBy;

    private Boolean hasActiveAgentAccess;

    private Long agentAccessExpiresAt;

    private Boolean sessionEnded;

    /** Who ended the conversation for good (the owner), or null. */
    private Long endedBy;

    private SessionInfo session;

    /** The developer panel with the counselor's inner state (admins, or development mode). */
    private Boolean counselorStateAvailable;

}