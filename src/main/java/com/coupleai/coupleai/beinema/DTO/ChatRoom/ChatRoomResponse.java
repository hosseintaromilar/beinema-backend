package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ChatRoomResponse {

    private Long id;

    private String title;

    private ChatRoomStatus status;

    private List<AgentSummary> agents;

}