package com.cartmate.cartmate.boundedContext.wallet.app.dto;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;

public record WalletChargeOrderResponse(
        String orderNumber,
        Long amount
) {
    public static WalletChargeOrderResponse from(WalletChargeOrder chargeOrder) {
        return new WalletChargeOrderResponse(
                chargeOrder.getOrderNumber(),
                chargeOrder.getAmount()
        );
    }
}
