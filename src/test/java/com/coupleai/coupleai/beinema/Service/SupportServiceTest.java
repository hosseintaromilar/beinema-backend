package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.SupportTicket;
import com.coupleai.coupleai.beinema.Entity.SupportTicketMessage;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.SupportTicketStatus;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Repository.SupportTicketMessageRepository;
import com.coupleai.coupleai.beinema.Repository.SupportTicketRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportServiceTest {

    private SupportTicketRepository tickets;
    private SupportTicketMessageRepository messages;
    private CurrentUserService current;
    private SupportService service;
    private User sara;
    private User admin;

    private static User user(long id, String name) {
        User user = User.builder().name(name).email(id + "@users.beinema").password("x")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        tickets = mock(SupportTicketRepository.class);
        messages = mock(SupportTicketMessageRepository.class);
        current = mock(CurrentUserService.class);
        service = new SupportService(tickets, messages, mock(UserRepository.class), current);
        sara = user(1L, "سارا");
        admin = user(9L, "پشتیبان");
        when(tickets.save(any(SupportTicket.class))).thenAnswer(call -> {
            SupportTicket ticket = call.getArgument(0);
            if (ticket.getId() == null) {
                ReflectionTestUtils.setField(ticket, "id", 7L);
            }
            return ticket;
        });
        when(messages.findAllByTicketIdOrderByIdAsc(7L)).thenReturn(List.of());
    }

    private SupportTicket ticketOf(User owner, SupportTicketStatus status) {
        SupportTicket ticket = new SupportTicket();
        ReflectionTestUtils.setField(ticket, "id", 7L);
        ticket.setUserId(owner.getId());
        ticket.setSubject("مشکل پرداخت");
        ticket.setCategory("PAYMENT");
        ticket.setStatus(status);
        when(tickets.findById(7L)).thenReturn(Optional.of(ticket));
        return ticket;
    }

    @Test
    void aNewTicketIsOpenWithTheFirstMessage() {
        when(current.getCurrentUser()).thenReturn(sara);

        SupportService.TicketView view = service.create(" مشکل پرداخت ", "PAYMENT", " پولم کم شد ولی اعتبار نیامد ");

        assertEquals("OPEN", view.ticket().status());
        assertEquals("مشکل پرداخت", view.ticket().subject());
        ArgumentCaptor<SupportTicketMessage> message = ArgumentCaptor.forClass(SupportTicketMessage.class);
        verify(messages).save(message.capture());
        assertFalse(message.getValue().isFromSupport());
        assertEquals("پولم کم شد ولی اعتبار نیامد", message.getValue().getBody());
    }

    @Test
    void emptyTicketsAndTooManyOpenOnesAreRefused() {
        when(current.getCurrentUser()).thenReturn(sara);
        assertThrows(IllegalArgumentException.class, () -> service.create(" ", "OTHER", "متن"));
        assertThrows(IllegalArgumentException.class, () -> service.create("موضوع", "OTHER", " "));
        when(tickets.countByUserIdAndStatusNot(1L, SupportTicketStatus.CLOSED)).thenReturn(5L);
        assertThrows(IllegalArgumentException.class, () -> service.create("موضوع", "OTHER", "متن"));
    }

    @Test
    void supportAnswersThePersonReadsAndAReplyOpensItAgain() {
        SupportTicket ticket = ticketOf(sara, SupportTicketStatus.OPEN);

        when(current.getCurrentUser()).thenReturn(admin);
        service.adminReply(7L, "بررسی شد و اعتبار برگشت.");
        assertEquals(SupportTicketStatus.ANSWERED, ticket.getStatus());
        assertTrue(ticket.isUnreadByUser());

        when(current.getCurrentUser()).thenReturn(sara);
        service.openMine(7L);
        assertFalse(ticket.isUnreadByUser(), "opening the ticket reads the answer");

        service.closeMine(7L);
        assertEquals(SupportTicketStatus.CLOSED, ticket.getStatus());
        service.replyMine(7L, "دوباره همین مشکل پیش آمد");
        assertEquals(SupportTicketStatus.OPEN, ticket.getStatus(), "a new message opens a closed ticket again");
        verify(messages, atLeastOnce()).save(any());
    }

    @Test
    void nobodyReadsSomeoneElsesTicket() {
        ticketOf(sara, SupportTicketStatus.OPEN);
        when(current.getCurrentUser()).thenReturn(user(2L, "علی"));
        assertThrows(IllegalArgumentException.class, () -> service.openMine(7L));
        assertThrows(IllegalArgumentException.class, () -> service.replyMine(7L, "سلام"));
    }
}
