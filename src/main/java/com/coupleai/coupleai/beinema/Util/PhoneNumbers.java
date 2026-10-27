package com.coupleai.coupleai.beinema.Util;

import com.coupleai.coupleai.beinema.Exception.InvalidContactException;

public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {

        if (raw == null || raw.isBlank()) {
            throw new InvalidContactException(
                    "لطفاً شماره موبایل را وارد کنید."
            );
        }

        // Persian (۰-۹) and Arabic (٠-٩) digits count as digits; a phone keyboard may type them
        StringBuilder latin = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if (c >= '۰' && c <= '۹') {
                latin.append((char) ('0' + (c - '۰')));
            } else if (c >= '٠' && c <= '٩') {
                latin.append((char) ('0' + (c - '٠')));
            } else {
                latin.append(c);
            }
        }
        String digits = latin.toString().replaceAll("[^0-9]", "");

        if (digits.startsWith("98") && digits.length() == 12) {
            digits = "0" + digits.substring(2);
        }

        if (digits.startsWith("9") && digits.length() == 10) {
            digits = "0" + digits;
        }

        if (!digits.matches("09[0-9]{9}")) {
            throw new InvalidContactException(
                    "شماره موبایل درست نیست؛ آن را مثل ۰۹۱۲۳۴۵۶۷۸۹ وارد کنید."
            );
        }

        return digits;
    }
}
