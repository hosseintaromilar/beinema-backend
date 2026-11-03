package com.coupleai.coupleai.beinema.ai;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;

/** What went wrong with a Metis call, in the few kinds the site acts on. The user never sees these names. */
public enum AiError {
    /** The account has no credit left: nothing works until it is charged. */
    NO_CREDIT,
    /** Too many requests for now; it passes by itself. */
    RATE_LIMIT,
    /** No answer in time. */
    TIMEOUT,
    /** Metis or the model behind it failed, or the connection broke. */
    UPSTREAM,
    /** Metis refused what we sent: a bug on our side. */
    BAD_REQUEST;

    /** The kind of an HTTP answer from Metis; null when it was a success. */
    public static AiError ofStatus(int status, String body) {
        if (status >= 200 && status < 300) {
            return null;
        }
        String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (status == 402 || (status >= 400 && status < 500
                && (text.contains("credit") || text.contains("balance") || text.contains("اعتبار") || text.contains("شارژ")))) {
            return NO_CREDIT;
        }
        if (status == 429) {
            return RATE_LIMIT;
        }
        if (status == 408 || status == 504) {
            return TIMEOUT;
        }
        if (status >= 500) {
            return UPSTREAM;
        }
        return BAD_REQUEST;
    }

    /** The kind of any failure of a call, looking through the wrapped causes. */
    public static AiError of(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MetisCallException call) {
                return call.error();
            }
            if (cause instanceof org.springframework.web.client.RestClientResponseException response) {
                return ofStatus(response.getStatusCode().value(), response.getResponseBodyAsString());
            }
            if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                return TIMEOUT;
            }
            if (cause instanceof IOException || cause instanceof org.springframework.web.client.ResourceAccessException) {
                return UPSTREAM;
            }
        }
        return UPSTREAM;
    }

    /** Whether a failure of this kind says the service itself is in trouble (not a bug of ours). */
    public boolean outage() {
        return this != BAD_REQUEST;
    }
}
