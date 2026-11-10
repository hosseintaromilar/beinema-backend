package com.coupleai.coupleai.beinema.billing.payment;

public interface PaymentProvider {

    String charge(Long userId, Long amount);
}
