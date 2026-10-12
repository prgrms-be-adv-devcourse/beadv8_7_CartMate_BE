package com.cartmate.cartmate.boundedContext.wallet.app.dto;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;

import java.time.LocalDateTime;

public record WalletTransactionResponse(
        Long transactionId,
        WalletTransactionType transactionType,
        Long amount,
        Long balanceAfter,
        LocalDateTime createdAt
) {

    public static WalletTransactionResponse from(WalletTransaction walletTransaction) {
        return new WalletTransactionResponse(
                walletTransaction.getId(),
                walletTransaction.getType(),
                walletTransaction.getAmount(),
                walletTransaction.getBalanceAfter(),
                walletTransaction.getCreatedAt()
        );
    }
}
