package com.coupleai.coupleai.beinema.ai;

/** The site cannot do this while the AI service is down; the person gets the one outage sentence. */
public class AiUnavailableException extends RuntimeException {

    public static final String TEXT = "سرویس موقتاً از دسترس خارج شده است. چند دقیقه دیگر دوباره امتحان کنید.";

    public AiUnavailableException() {
        super(TEXT);
    }
}
