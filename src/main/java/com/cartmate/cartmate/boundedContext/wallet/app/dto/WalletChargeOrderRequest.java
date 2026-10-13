package com.cartmate.cartmate.boundedContext.wallet.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletChargeOrderRequest(
        @NotNull
        @Positive
        Long amount,

        String idempotencyKey
) {

}

