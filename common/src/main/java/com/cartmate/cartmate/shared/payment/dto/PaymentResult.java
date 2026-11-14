package com.cartmate.cartmate.shared.payment.dto;

import java.time.LocalDateTime;

/**
 * 결제 서버가 다른 모듈에 돌려주는 결제 결과.
 * 서버를 나누면 이 모양 그대로 결제 서버 API의 응답이 된다.
 */
public record PaymentResult(
        Long paymentId,
        Long orderId,
        Long amount,
        Long refundedAmount,
        Long refundableAmount,
        String type,
        String status,
        LocalDateTime paidAt,
        LocalDateTime canceledAt
) {
    public boolean isPaid() {
        return "PAID".equals(status) || "PARTIALLY_REFUNDED".equals(status);
    }
}