package com.coupleai.coupleai.beinema.Exception;

import org.springframework.http.HttpStatus;

/** A clarifying question cannot take this answer: not found, closed, already answered, or not addressed to this partner. */
public class QuestionAnswerException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public QuestionAnswerException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
