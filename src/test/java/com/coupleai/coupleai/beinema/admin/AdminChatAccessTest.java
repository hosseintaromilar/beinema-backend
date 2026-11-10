package com.coupleai.coupleai.beinema.admin;

import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminChatAccessTest {

    private static ChatRoomParticipant person(Boolean allowed) {
        User user = new User();
        user.setAdminChatAccess(allowed);
        ChatRoomParticipant participant = new ChatRoomParticipant();
        participant.setUser(user);
        return participant;
    }

    @Test
    void aChatOpensOnlyWhenEveryoneInItAllowed() {
        assertTrue(AdminAccountService.chatAllowed(List.of(person(true), person(true))));
        assertFalse(AdminAccountService.chatAllowed(List.of(person(true), person(false))));
        assertFalse(AdminAccountService.chatAllowed(List.of(person(true), person(null))));
        assertFalse(AdminAccountService.chatAllowed(List.of()));
    }
}
