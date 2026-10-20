package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;

public interface PaymentWalletClient {
    Long getWalletId(Long memberId);
    Long getBalance(Long memberId);
    void use(Long walletId, Long amount, Long paymentId, Long orderId, PaymentType type);
}