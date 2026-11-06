package com.cartmate.cartmate.boundedContext.paymentService.wallet.app.dto;

import com.cartmate.cartmate.boundedContext.paymentService.wallet.domain.WalletWithdrawOrder;

public record WalletWithdrawOrderResponse(
        Long withdrawOrderId,
        Long amount
) {
    public static WalletWithdrawOrderResponse from(WalletWithdrawOrder withdrawOrder) {
        return new WalletWithdrawOrderResponse(
                withdrawOrder.getId(),
                withdrawOrder.getAmount()
        );
    }
}
