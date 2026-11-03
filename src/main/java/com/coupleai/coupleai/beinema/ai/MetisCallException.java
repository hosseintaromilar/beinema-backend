package com.coupleai.coupleai.beinema.ai;

/** A Metis call that failed, with its kind. The message is for the logs only. */
public class MetisCallException extends RuntimeException {

    private final AiError error;

    public MetisCallException(AiError error, String message) {
        super(message);
        this.error = error;
    }

    public AiError error() {
        return error;
    }

    /** Throws when the HTTP answer was not a success. */
    public static void check(int status, String body, String what) {
        AiError error = AiError.ofStatus(status, body);
        if (error != null) {
            String excerpt = body == null ? "" : body.substring(0, Math.min(body.length(), 300));
            throw new MetisCallException(error, what + " -> HTTP " + status + ": " + excerpt);
        }
    }
}
