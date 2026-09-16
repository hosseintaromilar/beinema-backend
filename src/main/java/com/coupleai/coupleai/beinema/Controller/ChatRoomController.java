package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.ChatRoom.ChatRoomResponse;
import com.coupleai.coupleai.beinema.DTO.ChatRoom.CreateChatRoomRequest;
import com.coupleai.coupleai.beinema.Service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {


    private final ChatRoomService chatRoomService;


    @PostMapping
    public ResponseEntity<ChatRoomResponse>

    createChatRoom(

            Authentication authentication,

            @RequestBody CreateChatRoomRequest request

    ) {


        return ResponseEntity.ok(

                chatRoomService.createChatRoom(

                        authentication.getName(),

                        request

                )

        );

    }


    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getChatRooms(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                chatRoomService.getChatRooms(
                        authentication.getName()
                )
        );

    }


    @GetMapping("/{id}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(
            @PathVariable Long id,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                chatRoomService.getChatRoom(
                        id,
                        authentication.getName()
                )
        );
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChatRoom(
            @PathVariable Long id,
            Authentication authentication
    ) {

        chatRoomService.deleteChatRoom(
                id,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }


    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveChatRoom(
            @PathVariable Long id,
            Authentication authentication
    ) {

        chatRoomService.leaveChatRoom(
                id,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }

}