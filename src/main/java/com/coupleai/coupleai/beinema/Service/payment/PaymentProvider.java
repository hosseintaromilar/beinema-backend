package com.coupleai.coupleai.beinema.Service.payment;

public interface PaymentProvider {

    String charge(Long userId, Long amount);
}
