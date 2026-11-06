package com.cartmate.cartmate.boundedContext.paymentService.wallet.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletWithdrawConfirmRequest(
        @NotNull
        @Positive
        Long withdrawOrderId
) {
}
