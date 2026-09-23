package com.coupleai.coupleai.beinema.Exception;

public class CommitmentRequiredException extends RuntimeException {

    public static final String REENTRY = "COMMITMENT_REQUIRED";
    public static final String SESSION_CONSENT = "SESSION_CONSENT_REQUIRED";
    public static final String SESSION_CONSENT_WAITING = "SESSION_CONSENT_WAITING";

    private final String code;

    public CommitmentRequiredException(String message) {
        this(REENTRY, message);
    }

    public CommitmentRequiredException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? REENTRY : code;
    }

    public String getCode() {
        return code;
    }
}
