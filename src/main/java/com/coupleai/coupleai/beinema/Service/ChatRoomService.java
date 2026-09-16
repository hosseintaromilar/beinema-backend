package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.*;
import com.coupleai.coupleai.beinema.Entity.*;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final ChatRealtimeService chatRealtimeService;
    private final MetisService metisService;
    private final AgentAccessService agentAccessService;


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

        AgentPlan accessPlan = agentAccessService.requireDefaultPlan(agent);
        agentAccessService.assertCanAfford(user, accessPlan);


        /*
         * =====================================================
         * 1. ساخت Conversation در Metis
         * =====================================================
         *
         * هنوز ChatRoom را save نکرده‌ایم.
         *
         * اگر Metis خطا بدهد، اصلاً ChatRoom ساخته نمی‌شود.
         * موجودی قبل از این فراخوانی چک شده تا گفتگوی بی‌استفاده ساخته نشود.
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

        agentAccessService.grantForNewRoom(user, chatRoom, agent, accessPlan);


        /*
         * =====================================================
         * 5. Response
         * =====================================================
         */

        return withAccessState(
                ChatRoomResponse.builder()

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

                .createdBy(user.getId()),
                chatRoom
        );
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
                participantRepository.findAllWithRoomByUserId(user.getId());

        if (participants.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = participants.stream()
                .map(ChatRoomParticipant::getChatRoom)
                .filter(Objects::nonNull)
                .map(ChatRoom::getId)
                .toList();

        Map<Long, LocalDateTime> lastUserMessageAt =
                lastUserMessageAtByRoom(roomIds, user.getId());

        return participants.stream()
                .map(participant -> {
                    ChatRoom chatRoom = participant.getChatRoom();
                    if (chatRoom == null) {
                        return null;
                    }

                    LocalDateTime activity = lastUserMessageAt.get(chatRoom.getId());
                    if (activity == null) {
                        activity = later(
                                participant.getCreatedAt(),
                                chatRoom.getCreatedAt()
                        );
                    }

                    return toChatRoomResponseSafe(chatRoom, toEpochMillis(activity));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        ChatRoomResponse::getLastActivityAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

    }

    private Map<Long, LocalDateTime> lastUserMessageAtByRoom(
            List<Long> roomIds,
            Long userId
    ) {
        Map<Long, LocalDateTime> lastUserMessageAt = new HashMap<>();

        if (roomIds.isEmpty()) {
            return lastUserMessageAt;
        }

        for (Object[] row : messageRepository.findLastUserMessageAtByRoomIds(
                roomIds,
                userId,
                MessageSenderType.USER
        )) {
            if (row == null || row[0] == null || row[1] == null) {
                continue;
            }

            lastUserMessageAt.put(
                    ((Number) row[0]).longValue(),
                    toLocalDateTime(row[1])
            );
        }

        return lastUserMessageAt;
    }

    private ChatRoomResponse toChatRoomResponseSafe(
            ChatRoom chatRoom,
            Long lastActivityAt
    ) {
        try {
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

            return withAccessState(
                    ChatRoomResponse.builder()
                            .id(chatRoom.getId())
                            .title(chatRoom.getTitle())
                            .status(chatRoom.getStatus())
                            .agents(agents)
                            .participants(toParticipantSummaries(chatRoom))
                            .lastActivityAt(lastActivityAt)
                            .createdBy(chatRoom.getCreatedBy()),
                    chatRoom
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private static LocalDateTime later(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null || first.isAfter(second)) {
            return first;
        }
        return second;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private static Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        return time.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
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


    public void leaveChatRoom(
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

        ChatRoomParticipant participant =
                participantRepository.findByChatRoomAndUser(chatRoom, user)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "شما عضو این گفتگو نیستید."
                                ));

        boolean isOwner =
                chatRoom.getCreatedBy().equals(user.getId())
                        || participant.getRole() == ParticipantRole.OWNER
                        || participant.getRole() == ParticipantRole.PARTNER_A;

        if (isOwner) {
            throw new IllegalArgumentException(
                    "مالک نمی‌تواند از گفتگو خارج شود. در صورت نیاز گفتگو را حذف کنید."
            );
        }

        announceLeave(chatRoom, user);
        participantRepository.deleteByChatRoomAndUser(chatRoom, user);
    }

    private void announceLeave(ChatRoom chatRoom, User user) {
        int nextSequence = messageRepository
                .findMaxSequenceNumberByChatRoom(chatRoom)
                .orElse(0)
                + 1;

        Message event = Message.builder()
                .chatRoom(chatRoom)
                .senderType(MessageSenderType.SYSTEM)
                .senderId(user.getId())
                .content(user.getName() + " از گفتگو خارج شد")
                .status(MessageStatus.SENT)
                .sequenceNumber(nextSequence)
                .build();

        messageRepository.saveAndFlush(event);
        chatRealtimeService.publish(chatRoom.getId(), event);
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

        return withAccessState(
                ChatRoomResponse.builder()
                        .id(chatRoom.getId())
                        .title(chatRoom.getTitle())
                        .status(chatRoom.getStatus())
                        .agents(agents)
                        .participants(toParticipantSummaries(chatRoom))
                        .createdBy(chatRoom.getCreatedBy()),
                chatRoom
        );
    }


    private ChatRoomResponse withAccessState(
            ChatRoomResponse.ChatRoomResponseBuilder builder,
            ChatRoom chatRoom
    ) {
        Long agentId = chatRoomAgentRepository
                .findActiveAgentIds(chatRoom.getId())
                .stream()
                .findFirst()
                .orElse(null);

        boolean hasAccess = agentAccessService.hasValidAccess(chatRoom.getId(), agentId);
        Long expiresAt = agentAccessService.findActive(chatRoom.getId(), agentId)
                .map(access -> access.getExpiresAt() == null
                        ? null
                        : access.getExpiresAt()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli())
                .orElse(null);

        return builder
                .hasActiveAgentAccess(hasAccess)
                .agentAccessExpiresAt(expiresAt)
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