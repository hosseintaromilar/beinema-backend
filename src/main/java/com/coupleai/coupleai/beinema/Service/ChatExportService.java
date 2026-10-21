package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.MessageResponse;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Exception.ChatRoomAccessDeniedException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Lets a member download the whole chat of a room as a PDF. */
@Service
@RequiredArgsConstructor
public class ChatExportService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final CurrentUserService currentUserService;
    private final MessageService messageService;
    private final SessionLifecycleService sessionLifecycleService;

    private final ChatPdfExporter exporter = new ChatPdfExporter();

    public record Exported(byte[] bytes, String title) {
    }

    public Exported exportPdf(Long chatRoomId) throws IOException {
        User user = currentUserService.getCurrentUser();
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        if (!participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new ChatRoomAccessDeniedException("Access denied");
        }

        // same messages (and the same access rules) as the chat screen
        List<MessageResponse> messages = messageService.getMessages(chatRoomId);

        List<String> members = new ArrayList<>();
        for (ChatRoomParticipant participant : participantRepository.findAllByChatRoom(room)) {
            if (participant.getUser() != null && participant.getUser().visibleName() != null) {
                members.add(participant.getUser().visibleName());
            }
        }
        String agentName = chatRoomAgentRepository.findAllByChatRoom(room).stream()
                .filter(link -> Boolean.TRUE.equals(link.getActive()) && link.getAgent() != null)
                .map(link -> link.getAgent().getName())
                .findFirst()
                .orElse("");

        int sessions = sessionLifecycleService.describe(room).getNumber();
        byte[] pdf = exporter.render(
                new ChatPdfExporter.Header(room.getTitle(), members, agentName, user.visibleName(), sessions),
                messages,
                user.getId());
        return new Exported(pdf, room.getTitle());
    }
}
