package com.coupleai.coupleai.beinema.Exception;

/**
 * This person cannot write in a session right now. The code says why:
 * SESSION_BREAK (between two sessions), SESSION_ENTRY (they have not entered
 * the running session yet), SESSION_REST (they took the break and the others
 * go on), SESSION_LIMIT (the most sessions allowed in 24 hours were held).
 */
public class SessionOnBreakException extends RuntimeException {

    public static final String BREAK = "SESSION_BREAK";
    public static final String ENTRY = "SESSION_ENTRY";
    public static final String REST = "SESSION_REST";
    public static final String LIMIT = "SESSION_LIMIT";

    private final String code;

    public SessionOnBreakException(String message) {
        this(BREAK, message);
    }

    public SessionOnBreakException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
