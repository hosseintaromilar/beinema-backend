package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Service.MetisService;
import com.coupleai.coupleai.beinema.DTO.metis.MetisConversationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A companion with its own copy of the v3 workflow runs on it; every other room on the couple counselor's. */
class CompanionWorkflowTest {

    private static final String COUPLE = "couple-v3";
    private static final String FINANCE = "finance-v3";

    private CounselingConfig config;

    @BeforeEach
    void setUp() {
        config = new CounselingConfig();
        config.setWorkflowId(COUPLE);
        config.setCompanionWorkflowIds(" finance-v3 , parenting-v3 ");
    }

    @Test
    void aCompanionsOwnWorkflowIsUsedAndAnythingElseFallsBackToTheCoupleOne() {
        assertTrue(config.isCompanionWorkflow("finance-v3"));
        assertEquals(FINANCE, config.workflowFor(FINANCE));
        assertEquals("parenting-v3", config.workflowFor("parenting-v3"));
        assertEquals(COUPLE, config.workflowFor("the-old-v2-bot"), "the couple counselor's agent still carries the v2 id");
        assertEquals(COUPLE, config.workflowFor(null));
        assertFalse(config.isCompanionWorkflow(""));
    }

    @Test
    void theGatewaySendsEveryLaneOfARoomToItsCompanionsWorkflow() {
        MetisService metis = mock(MetisService.class);
        ChatRoomAgentRepository links = mock(ChatRoomAgentRepository.class);
        MetisConversationResponse session = new MetisConversationResponse();
        session.setId("s1");
        when(metis.createConversation(anyString(), anyString(), anyString())).thenReturn(session);
        when(metis.sendMessageAllowBlank(eq("s1"), anyString())).thenReturn("{}");

        ChatRoom room = new ChatRoom();
        room.setId(42L);
        when(links.findActiveAgentBotIds(42L)).thenReturn(List.of(FINANCE));

        MetisWorkflowGateway gateway = new MetisWorkflowGateway(metis, config, links);
        gateway.forRoom(room).director("##BEINEMA_MODE=DIRECTOR##");
        verify(metis).createConversation(eq(FINANCE), anyString(), anyString());

        gateway.voice("##BEINEMA_MODE=VOICE##");
        verify(metis).createConversation(eq(COUPLE), anyString(), anyString());
    }
}
