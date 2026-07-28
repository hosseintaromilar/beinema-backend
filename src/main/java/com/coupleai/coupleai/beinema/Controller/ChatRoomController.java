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


}