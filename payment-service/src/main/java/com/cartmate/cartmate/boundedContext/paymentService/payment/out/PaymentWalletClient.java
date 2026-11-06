package com.cartmate.cartmate.boundedContext.paymentService.payment.out;

import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentType;

public interface PaymentWalletClient {
    Long getWalletId(Long memberId);
    Long getBalance(Long memberId);
    void use(Long walletId, Long amount, Long paymentId, Long orderId, PaymentType type);
    void refund(Long walletId, Long amount, Long paymentId, Long orderId, Long refundId);
}