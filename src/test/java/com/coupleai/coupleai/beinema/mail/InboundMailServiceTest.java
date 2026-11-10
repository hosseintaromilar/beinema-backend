package com.coupleai.coupleai.beinema.mail;

import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.support.SupportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboundMailServiceTest {

    @TempDir
    Path dir;

    private final SupportService support = mock(SupportService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final InboundMailService service = new InboundMailService(support, users, "");

    private Path mail(String text) throws Exception {
        Path file = dir.resolve("m" + System.nanoTime());
        Files.writeString(file, text.replace("\n", "\r\n"), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void aReplyBecomesATicketWithoutTheQuotedMail() throws Exception {
        when(users.findByEmailNormalized(anyString())).thenReturn(Optional.empty());
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(support.fromEmail(any(), anyString(), anyString(), any(), anyString(), any())).thenReturn(7L);
        Path file = mail("""
                From: Sara <Sara@Gmail.com>
                To: support@beinema.ir
                Subject: Re: =?UTF-8?B?2qnYryDYqtij24zbjNiv?= [#7]
                Message-ID: <abc@mail.gmail.com>
                MIME-Version: 1.0
                Content-Type: text/plain; charset=UTF-8

                ممنون، درست شد.

                On Wed, Nov 4, 2026 at 2:36 PM support wrote:
                > پیام قبلی
                """);
        String outcome = service.read("support", file);
        assertTrue(outcome.startsWith("ticket 7"), outcome);
        verify(support).fromEmail(eq(null), eq("sara@gmail.com"), eq("support"), eq("Re: کد تأیید [#7]"),
                eq("ممنون، درست شد."), eq("<abc@mail.gmail.com>"));
    }

    @Test
    void automaticRepliesAndOurOwnMailAreDropped() throws Exception {
        Path away = mail("""
                From: someone@example.com
                Subject: Out of office
                Auto-Submitted: auto-replied
                Content-Type: text/plain

                I am away.
                """);
        Path ours = mail("""
                From: no-reply@beinema.ir
                Subject: loop
                Content-Type: text/plain

                hi
                """);
        assertTrue(service.read("support", away).contains("automatic"));
        assertTrue(service.read("info", ours).contains("our own"));
        verify(support, never()).fromEmail(any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void thePageVersionIsReadWhenThereIsNoPlainText() throws Exception {
        assertEquals("سلام\nخوبی؟", InboundMailService.fromHtml("<p>سلام</p><div>خوبی؟</div>").strip());
        assertEquals("متن من", InboundMailService.withoutQuote("متن من\n\nدر تاریخ ۱۴ آبان، پشتیبانی بین‌ما نوشت:\n> قبلی"));
    }
}
