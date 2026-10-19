package com.cartmate.cartmate.shared.payment.event;

import java.time.LocalDateTime;

// 결제 취소(예치금 환불 완료). order가 받아서 주문 취소를 마무리한다
public record PaymentCanceledEvent(
        Long paymentId,
        Long orderId,
        Long memberId,
        Long amount,
        LocalDateTime canceledAt
) {
}