package com.coupleai.coupleai.beinema.auth.sms;

public interface SmsProvider {

    void send(String toPhone, String text);
}
