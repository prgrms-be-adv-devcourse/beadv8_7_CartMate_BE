package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;

import java.time.LocalDateTime;

public interface PaymentOrderClient {
    OrderPaymentInfo getPaymentInfo(Long orderId, Long memberId, LocalDateTime now);
    void applyPayment(Long orderId, Long memberId, LocalDateTime paidAt);
}