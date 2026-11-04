package com.coupleai.coupleai.beinema.mail;

/**
 * A wrong six-digit code. The methods that check a code keep their transaction when this is
 * thrown (noRollbackFor), so the count of wrong tries is saved and the code is locked after five;
 * with an ordinary error the count would roll back and the code could be guessed.
 */
public class WrongCodeException extends IllegalArgumentException {

    public WrongCodeException(String message) {
        super(message);
    }
}
