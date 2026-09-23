package com.coupleai.coupleai.beinema.governance;

public final class ConsentReplay {

    private ConsentReplay() {
    }

    public static boolean asksAgain(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.replace("\u200c", "");
        boolean mentionsAgreement = normalized.contains("موافقت")
                || normalized.contains("موافق هست")
                || normalized.contains("موافقید")
                || normalized.contains("موافق هستید");
        boolean mentionsFrame = normalized.contains("قوانین")
                || normalized.contains("چارچوب");
        return mentionsAgreement && mentionsFrame;
    }
}
