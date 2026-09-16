package com.coupleai.coupleai.beinema.Service.payment;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ManualPaymentProvider implements PaymentProvider {

    @Override
    public String charge(Long userId, Long amount) {
        return "DEV-" + userId + "-" + amount + "-" + UUID.randomUUID();
    }
}
