package com.cartmate.cartmate.boundedContext.payment.domain;

public enum PaymentStatus {
    PAID,               // 결제 완료
    PARTIALLY_REFUNDED, // 일부 환불
    CANCELED            // 전액 환불
}