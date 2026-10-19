package com.cartmate.cartmate.shared.payment.event;

import java.time.LocalDateTime;

// 개인 주문 결제 완료. order가 받아서 주문을 결제 완료 상태로 바꾼다
public record PaymentCompletedEvent(
        Long paymentId,
        Long orderId,
        Long memberId,
        Long amount,
        LocalDateTime paidAt
) {
}