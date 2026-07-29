package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.*;
import com.coupleai.coupleai.beinema.Entity.*;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Exception.*;
import com.coupleai.coupleai.beinema.Repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    private final ChatRoomParticipantRepository
            participantRepository;

    private final ChatRoomAgentRepository
            chatRoomAgentRepository;

    private final AgentRepository agentRepository;

    private final UserRepository userRepository;


    public ChatRoomResponse createChatRoom(


            String email,

            CreateChatRoomRequest request

    ) {

        User user = userRepository

                .findByEmail(email)

                .orElseThrow(() ->

                        new RuntimeException(

                                "User not found"

                        )

                );


        Agent agent = agentRepository

                .findById(request.getAgentId())

                .orElseThrow(() ->

                        new RuntimeException(

                                "Agent not found"

                        )

                );


        if (

                agent.getStatus() != AgentStatus.ACTIVE

        ) {

            throw new RuntimeException(

                    "Agent is not active"

            );

        }

        String title = request.getTitle();

        if(title == null || title.isBlank()){
            title = "گفتگوی جدید";
        }
        ChatRoom chatRoom = ChatRoom.builder()

                .title(

                        title

                )

                .status(

                        ChatRoomStatus.ACTIVE

                )
                .createdBy(
                        user.getId()
                )

                .build();


        chatRoomRepository.save(chatRoom);


        ChatRoomParticipant participant =

                ChatRoomParticipant.builder()

                        .chatRoom(chatRoom)

                        .user(user)

                        .role(

                                ParticipantRole.OWNER

                        )

                        .build();


        participantRepository.save(participant);


        ChatRoomAgent chatRoomAgent =

                ChatRoomAgent.builder()

                        .chatRoom(chatRoom)

                        .agent(agent)

                        .active(agent.getStatus() == AgentStatus.ACTIVE)

                        .addedAt(LocalDateTime.now())

                        .addedBy(user.getId())

                        .build();


        chatRoomAgentRepository.save(chatRoomAgent);


        return ChatRoomResponse.builder()

                .id(chatRoom.getId())

                .title(chatRoom.getTitle())

                .status(chatRoom.getStatus())

                .agents(

                        List.of(

                                AgentSummary.builder()

                                        .id(agent.getId())

                                        .name(agent.getName())

                                        .type(agent.getType().toString())

                                        .avatar(agent.getAvatar())

                                        .build()

                        )

                )

                .build();

    }

    public List<ChatRoomResponse> getChatRooms(String email) {

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<ChatRoomParticipant> participants =
                participantRepository.findAllByUser(user);

        return participants.stream()

                .map(ChatRoomParticipant::getChatRoom)

                .map(chatRoom -> {

                    List<AgentSummary> agents =
                            chatRoomAgentRepository
                                    .findAllByChatRoom(chatRoom)
                                    .stream()
                                    .map(chatRoomAgent -> {

                                        Agent agent = chatRoomAgent.getAgent();

                                        return AgentSummary.builder()
                                                .id(agent.getId())
                                                .name(agent.getName())
                                                .type(agent.getType().toString())
                                                .avatar(agent.getAvatar())
                                                .build();

                                    })
                                    .toList();

                    return ChatRoomResponse.builder()
                            .id(chatRoom.getId())
                            .title(chatRoom.getTitle())
                            .status(chatRoom.getStatus())
                            .agents(agents)
                            .build();

                })

                .toList();

    }

    public void deleteChatRoom(
            Long chatRoomId,
            String email
    ) {

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        ChatRoom chatRoom =
                chatRoomRepository.findById(chatRoomId)
                        .orElseThrow(() ->
                                new RuntimeException("Chat room not found"));

        boolean isOwner =
                participantRepository.existsByChatRoomAndUserAndRole(
                        chatRoom,
                        user,
                        ParticipantRole.OWNER
                );

        if (!isOwner) {

            throw new RuntimeException(
                    "Access denied"
            );
        }

        participantRepository.deleteAllByChatRoom(chatRoom);

        chatRoomAgentRepository.deleteAllByChatRoom(chatRoom);

        chatRoomRepository.delete(chatRoom);
    }
}