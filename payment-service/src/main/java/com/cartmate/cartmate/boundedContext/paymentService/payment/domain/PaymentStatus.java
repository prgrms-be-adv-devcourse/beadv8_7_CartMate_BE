package com.cartmate.cartmate.boundedContext.paymentService.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    PAID("결제 완료"),
    PARTIALLY_REFUNDED("부분 환불"),
    CANCELED("전액 환불");

    private final String label;
}