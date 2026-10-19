package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;

import java.time.LocalDateTime;

public interface PaymentSharedGroupOrderClient {
    SharedGroupOrderPaymentInfo getPaymentInfo(Long sharedGroupOrderId, Long memberId, LocalDateTime now);
    void markStockReserved(Long sharedGroupOrderId);
    void applyPayment(Long sharedGroupOrderId, Long memberId, LocalDateTime paidAt, TeamDeliveryAddress address);
}