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

        String digits = raw.replaceAll("[^0-9]", "");

        if (digits.startsWith("98") && digits.length() == 12) {
            digits = "0" + digits.substring(2);
        }

        if (digits.startsWith("9") && digits.length() == 10) {
            digits = "0" + digits;
        }

        if (!digits.matches("09[0-9]{9}")) {
            throw new InvalidContactException(
                    "فرمت شماره موبایل صحیح نیست."
            );
        }

        return digits;
    }
}
