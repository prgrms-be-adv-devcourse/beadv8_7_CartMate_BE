package com.cartmate.cartmate.boundedContext.wallet.app.dto;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;

public record WalletBalanceResponse(
        Long walletId,
        Long balance
) {
    public static WalletBalanceResponse from(Wallet wallet) {
        return new WalletBalanceResponse(
                wallet.getId(),
                wallet.getBalance()
        );
    }
}
