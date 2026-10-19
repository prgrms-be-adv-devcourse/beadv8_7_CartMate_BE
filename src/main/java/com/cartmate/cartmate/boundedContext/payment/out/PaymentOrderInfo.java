package com.cartmate.cartmate.boundedContext.payment.out;

// 결제에 필요한 주문 정보만
public record PaymentOrderInfo(
        Long orderId,
        Long memberId,
        Long amount,    // 서버가 계산한 최종 결제 금액 (프론트 금액은 믿지 않음)
        boolean payable // 결제 대기 상태인지
) {
}