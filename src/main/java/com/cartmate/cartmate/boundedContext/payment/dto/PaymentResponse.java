package com.cartmate.cartmate.boundedContext.payment.dto;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        Long amount,
        String type,
        String status,
        LocalDateTime paidAt,
        LocalDateTime canceledAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getType().name(),
                p.getStatus().name(), p.getPaidAt(), p.getCanceledAt());
    }
}