package com.cartmate.cartmate.boundedContext.wallet.app.dto;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletChargeConfirmRequest(
        @NotBlank
        String orderNumber,
        @NotBlank
        String paymentKey,
        @NotNull
        @Positive
        Long amount
) {
    public static WalletChargeConfirmRequest from(WalletChargeOrder chargeOrder) {
        return new WalletChargeConfirmRequest(
                chargeOrder.getOrderNumber(),
                chargeOrder.getPaymentKey(),
                chargeOrder.getAmount()
        );
    }
}
