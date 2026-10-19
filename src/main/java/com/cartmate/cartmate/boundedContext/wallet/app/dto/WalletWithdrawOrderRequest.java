package com.cartmate.cartmate.boundedContext.wallet.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletWithdrawOrderRequest(
        @NotNull
        @Positive
        Long amount,

        @NotBlank
        String idempotencyKey
) {
}
