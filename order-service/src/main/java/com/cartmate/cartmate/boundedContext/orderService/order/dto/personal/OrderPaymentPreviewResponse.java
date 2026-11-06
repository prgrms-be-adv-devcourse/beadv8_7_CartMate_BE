package com.cartmate.cartmate.boundedContext.orderService.order.dto.personal;

import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

// 결제 화면: 주문 금액·결제 가능 여부(주문) + 예치금 잔액(결제)
public record OrderPaymentPreviewResponse(
        Long orderId,
        Long amount,
        boolean payable,
        Long walletBalance,
        Long balanceAfter,
        boolean sufficient,
        PaymentResult existingPayment, // 이미 결제했으면 그 결과, 아니면 null
        Long orderAmount,              // 할인 전 주문 금액 (amount = orderAmount - discountAmount)
        Long discountAmount,           // 쿠폰 할인액 (쿠폰 없으면 0)
        Long memberCouponId            // 미리보기에 반영한 쿠폰 (없으면 null)
) {
}