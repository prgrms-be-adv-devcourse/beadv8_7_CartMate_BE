package com.cartmate.cartmate.boundedContext.payment.dto;

public record PaymentPreviewResponse(
        Long orderId,
        Long amount,
        boolean payable,
        Long walletBalance,
        Long balanceAfter,
        boolean sufficient,
        PaymentResponse existingPayment // 이미 결제했으면 그 결과, 아니면 null
) {
}