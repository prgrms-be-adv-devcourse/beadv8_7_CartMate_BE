package com.cartmate.cartmate.boundedContext.payment.out;

public interface PaymentWalletClient {
    Long getWalletId(Long memberId);
    Long getBalance(Long memberId);
    void use(Long walletId, Long amount, Long paymentId, Long orderId);
    void refund(Long walletId, Long amount, Long paymentId, Long orderId, Long refundId);
}