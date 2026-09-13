package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.*;
import com.coupleai.coupleai.beinema.Entity.*;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

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
    private final MessageRepository messageRepository;
    private final ChatRoomInvitationRepository invitationRepository;
    private final MetisService metisService;


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


        if (
                title == null ||
                        title.isBlank()
        ) {

            title = "گفتگوی جدید";

        }


        /*
         * =====================================================
         * 1. ساخت Conversation در Metis
         * =====================================================
         *
         * هنوز ChatRoom را save نکرده‌ایم.
         *
         * اگر Metis خطا بدهد، اصلاً ChatRoom ساخته نمی‌شود.
         */

        var metisConversation =

                metisService.createConversation(
                        agent.getAiBotId(),
                        user
                );


        String aiConversationId =
                metisConversation.getId();


        /*
         * =====================================================
         * 2. ساخت ChatRoom
         * =====================================================
         */

        ChatRoom chatRoom = ChatRoom.builder()

                .title(title)

                .status(
                        ChatRoomStatus.ACTIVE
                )

                .aiConversationId(
                        aiConversationId
                )

                .createdBy(
                        user.getId()
                )

                .build();


        chatRoomRepository.save(chatRoom);


        /*
         * =====================================================
         * 3. اضافه کردن User به ChatRoom
         * =====================================================
         */

        ChatRoomParticipant participant =

                ChatRoomParticipant.builder()

                        .chatRoom(chatRoom)

                        .user(user)

                        .role(
                                ParticipantRole.OWNER
                        )

                        .build();


        participantRepository.save(participant);


        /*
         * =====================================================
         * 4. اضافه کردن Agent به ChatRoom
         * =====================================================
         */

        ChatRoomAgent chatRoomAgent =

                ChatRoomAgent.builder()

                        .chatRoom(chatRoom)

                        .agent(agent)

                        .active(
                                agent.getStatus()
                                        == AgentStatus.ACTIVE
                        )

                        .addedAt(
                                LocalDateTime.now()
                        )

                        .addedBy(
                                user.getId()
                        )

                        .build();


        chatRoomAgentRepository.save(
                chatRoomAgent
        );


        /*
         * =====================================================
         * 5. Response
         * =====================================================
         */

        return ChatRoomResponse.builder()

                .id(
                        chatRoom.getId()
                )

                .title(
                        chatRoom.getTitle()
                )

                .status(
                        chatRoom.getStatus()
                )

                .agents(

                        List.of(

                                AgentSummary.builder()

                                        .id(
                                                agent.getId()
                                        )

                                        .name(
                                                agent.getName()
                                        )

                                        .type(
                                                agent.getType().toString()
                                        )

                                        .avatar(
                                                agent.getAvatar()
                                        )

                                        .build()

                        )

                )

                .participants(
                        List.of(
                                ParticipantSummary.builder()
                                        .userId(user.getId())
                                        .name(user.getName())
                                        .role(ParticipantRole.OWNER)
                                        .build()
                        )
                )

                .build();
    }


    /*public ChatRoomResponse createChatRoom(


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

    }*/

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
                            .participants(toParticipantSummaries(chatRoom))
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

        boolean canDelete =
                chatRoom.getCreatedBy().equals(user.getId())
                        || participantRepository.existsByChatRoomAndUserAndRole(
                                chatRoom,
                                user,
                                ParticipantRole.OWNER
                        );

        if (!canDelete) {

            throw new RuntimeException(
                    "Access denied"
            );
        }

        invitationRepository.deleteAllByChatRoom(chatRoom);

        participantRepository.deleteAllByChatRoom(chatRoom);

        chatRoomAgentRepository.deleteAllByChatRoom(chatRoom);

        messageRepository.deleteAllByChatRoom(chatRoom);

        chatRoomRepository.delete(chatRoom);
    }


    public ChatRoomResponse getChatRoom(Long chatRoomId, String email) {

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        if (!participantRepository.existsByChatRoomAndUser(chatRoom, user)) {
            throw new RuntimeException("Access denied");
        }

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
                .participants(toParticipantSummaries(chatRoom))
                .build();
    }


    private List<ParticipantSummary> toParticipantSummaries(ChatRoom chatRoom) {

        return participantRepository.findAllByChatRoom(chatRoom)
                .stream()
                .map(item -> ParticipantSummary.builder()
                        .userId(item.getUser().getId())
                        .name(item.getUser().getName())
                        .role(item.getRole())
                        .build())
                .toList();
    }
}