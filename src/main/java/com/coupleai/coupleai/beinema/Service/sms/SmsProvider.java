package com.coupleai.coupleai.beinema.Service.sms;

public interface SmsProvider {

    void send(String toPhone, String text);
}
