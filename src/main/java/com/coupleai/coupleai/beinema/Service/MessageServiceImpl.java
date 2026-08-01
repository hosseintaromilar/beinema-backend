package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.DTO.messages.SendMessageRequest;
import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Mapper.MessageMapper;
import com.coupleai.coupleai.beinema.Repository.*;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    private final ChatRoomRepository chatRoomRepository;

    private final ChatRoomParticipantRepository participantRepository;

    private final CurrentUserService currentUserService;

    private final UserRepository userRepository;

    private final AgentRepository agentRepository;

    @Override
    public MessageResponse sendMessage(
            Long chatRoomId,
            SendMessageRequest request
    ) {

        User currentUser = currentUserService.getCurrentUser();

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() ->
                        new RuntimeException("Chat room not found"));

        if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
            throw new RuntimeException("Chat room is inactive");
        }

        boolean isParticipant =
                participantRepository.existsByChatRoomAndUser(
                        chatRoom,
                        currentUser
                );

        if (!isParticipant) {
            throw new RuntimeException("Access denied");
        }

        Integer nextSequenceNumber =
                messageRepository.findLastSequenceNumber(chatRoomId) + 1;

        Message message = MessageMapper.toEntity(
                request,
                chatRoom,
                currentUser.getId(),
                MessageSenderType.USER,
                nextSequenceNumber
        );

        messageRepository.save(message);

        return MessageMapper.toResponse(
                message,
                currentUser,
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long chatRoomId) {

        User currentUser = currentUserService.getCurrentUser();

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() ->
                        new RuntimeException("Chat room not found"));

        boolean isParticipant =
                participantRepository.existsByChatRoomAndUser(
                        chatRoom,
                        currentUser
                );

        if (!isParticipant) {
            throw new RuntimeException("Access denied");
        }

        return messageRepository
                .findByChatRoomIdAndDeletedFalseOrderBySequenceNumberAsc(chatRoomId)
                .stream()
                .map(message -> {

                    User user = null;
                    Agent agent = null;

                    if (message.getSenderType() == MessageSenderType.USER) {

                        user = userRepository
                                .findById(message.getSenderId())
                                .orElse(null);

                    } else {

                        agent = agentRepository
                                .findById(message.getSenderId())
                                .orElse(null);

                    }

                    return MessageMapper.toResponse(
                            message,
                            user,
                            agent
                    );

                })
                .toList();
    }
}