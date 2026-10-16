package com.cartmate.cartmate.boundedContext.wallet.app.dto;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletWithdrawOrder;

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
