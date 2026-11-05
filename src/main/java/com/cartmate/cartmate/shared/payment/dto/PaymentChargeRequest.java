package com.cartmate.cartmate.shared.payment.dto;

// 결제 서버 내부 API 요청: (구분·주문·회원) 키로 이 금액을 한 번만 차감
public record PaymentChargeRequest(
        PaymentOrderType orderType,
        Long orderId,
        Long memberId,
        long amount
) {
}