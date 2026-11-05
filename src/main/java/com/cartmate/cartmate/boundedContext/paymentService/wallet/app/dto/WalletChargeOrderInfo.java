package com.cartmate.cartmate.boundedContext.paymentService.wallet.app.dto;

import com.cartmate.cartmate.boundedContext.paymentService.wallet.domain.ChargeStatus;
import com.cartmate.cartmate.boundedContext.paymentService.wallet.domain.WalletChargeOrder;

// 충전 승인 전에 트랜잭션 밖에서 검증하려고 충전 주문의 필요한 값만 복사해 둔 것
public record WalletChargeOrderInfo(
        Long walletId,
        String orderNumber,
        Long amount,
        ChargeStatus status,
        String paymentKey,
        String idempotencyKey
) {
    public static WalletChargeOrderInfo from(WalletChargeOrder chargeOrder) {
        return new WalletChargeOrderInfo(
                chargeOrder.getWallet().getId(),
                chargeOrder.getOrderNumber(),
                chargeOrder.getAmount(),
                chargeOrder.getStatus(),
                chargeOrder.getPaymentKey(),
                chargeOrder.getIdempotencyKey()
        );
    }
}
