package com.coupleai.coupleai.beinema.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailAddressesTest {

    @Test
    void oneGmailPersonIsOneAddress() {
        assertEquals("ali@gmail.com", EmailAddresses.normalize(" A.li+x@Gmail.com "));
        assertEquals("ali@gmail.com", EmailAddresses.normalize("ali@googlemail.com"));
    }

    @Test
    void tagsAreDroppedElsewhereButDotsStay() {
        assertEquals("sara.k@outlook.com", EmailAddresses.normalize("Sara.K+shop@outlook.com"));
        assertEquals("reza@yahoo.com", EmailAddresses.normalize("reza-news@yahoo.com"));
    }

    @Test
    void throwawayMailboxesAndBadShapesAreRefused() {
        assertTrue(EmailAddresses.disposable("x@mailinator.com"));
        assertTrue(EmailAddresses.disposable("x@inbox.yopmail.com"));
        assertFalse(EmailAddresses.disposable("x@gmail.com"));
        assertFalse(EmailAddresses.valid("not an email"));
        assertTrue(EmailAddresses.valid("someone@beinema.ir"));
    }
}
