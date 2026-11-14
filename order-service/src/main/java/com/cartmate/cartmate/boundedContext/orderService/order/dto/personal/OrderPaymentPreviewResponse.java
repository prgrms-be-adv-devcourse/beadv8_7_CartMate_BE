package com.cartmate.cartmate.boundedContext.orderService.order.dto.personal;

import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

public record OrderPaymentPreviewResponse(
        Long orderId,
        Long amount,
        boolean payable,
        Long walletBalance,
        Long balanceAfter,
        boolean sufficient,
        PaymentResult existingPayment,
        Long orderAmount,
        Long discountAmount,
        Long memberCouponId
) {
}